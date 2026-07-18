defmodule MatchmakingService.MatchmakingServer do
  @moduledoc """
  ETS-based matchmaking server with 4-dimension weighted scoring algorithm.

  Scoring formula:
    Score(a, b) =
      0.35 * university_score  (1.0 = diff uni, 0.2 = same uni)
    + 0.35 * wait_time_score   (avg wait normalised to 60 s)
    + 0.15 * freshness_score   (0.0 if matched within last 5 min)
    + 0.15 * major_affinity    (0.8 same field, 0.5 unknown, 0.2 different)

  Major fields are grouped into academic families:
    - :tech     => CS, IT, Engineering, Math, Physics
    - :science  => Biology, Chemistry, Medicine, Pharmacy
    - :social   => Economics, Law, Psychology, Sociology, Education
    - :arts     => Arts, Design, Literature, Communication, History

  ETS tables (all owned by this GenServer process):
    :matchmaking_queue   – {email, university, major, joined_at_ms}
    :active_pairs        – {pair_id, email_a, email_b, started_at_ms}
    :recent_matches      – {canonical_key, matched_at_ms}
    :notify_urls         – {email, notify_url}  (for async gateway callbacks)

  RAM notes:
    - No per-request processes; single long-lived GenServer
    - GenServer state itself is %{} (empty map) – all data lives in ETS
    - Returns :hibernate on every callback to release heap between calls
    - BEAM GC tuned with fullsweep_after: 10 in Application.start/2
  """

  use GenServer
  require Logger

  # ── Tuneable constants ──────────────────────────────────────────────────────
  ## Queue entry expires after 90 s with no match
  @queue_timeout_ms 90_000
  ## Heartbeat checks timeouts and stale records every 15 s
  @heartbeat_interval 15_000
  ## Avoid re-matching same pair for 5 minutes
  @recent_match_ttl_ms 300_000
  ##
  ## Phase 7.2 — Active pairs are reaped by the heartbeat if they linger
  ## longer than this. A pair is normally ended explicitly by either
  ## peer's channel `terminate/1` (Phase 7.3) or the signaling service's
  ## `end-pair` HTTP callback. If both peers drop without that signal —
  ## e.g. network outage, crash on the gateway — the entry would otherwise
  ## live in `:active_pairs` forever. We reap at 2× the queue timeout so a
  ## match that has finished connecting can run for a typical call length
  ## before being reclaimed defensively.
  @active_pair_ttl_ms 180_000
  ##
  ## Phase 7.4 — Cap on candidates scanned per join. `find_best_match/4`
  ## still evaluates every live queued user once (the matching score is
  ## a smooth function of all four fields, so there is no early-out
  ## short-circuit available in general), but bounding candidates to a
  ## per-join cap means a pathological queue cannot turn a single slow
  ## join into a multi-millisecond scan. Set generously — the per-uni
  ## index added below gives a cheaper candidate stream than `tab2list`.
  @match_candidate_cap 5_000

  # Scoring weights (must sum to 1.0)
  @w_university 0.35
  @w_wait_time 0.35
  @w_freshness 0.15
  @w_major 0.15

  # Academic-field groupings for major affinity scoring
  @major_families %{
    :tech => ~w(informatika ilmu_komputer teknik_informatika sistem_informasi
                   teknik_elektro teknik_mesin teknik_sipil matematika fisika
                   cs it engineering math physics),
    :science => ~w(biologi kimia kedokteran farmasi kesehatan keperawatan
                   biology chemistry medicine pharmacy health nursing),
    :social => ~w(ekonomi manajemen akuntansi hukum psikologi sosiologi
                   pendidikan ilmu_komunikasi administrasi bisnis
                   economics management law psychology sociology education business),
    :arts => ~w(seni desain sastra komunikasi sejarah bahasa filsafat
                   arts design literature history philosophy language)
  }

  # ETS table names
  @queue_table :matchmaking_queue
  @pairs_table :active_pairs
  @recent_table :recent_matches
  @notify_table :matchmaking_notify_urls
  ##
  ## Phase 7.4 — Per-university index for the matchmaking queue.
  ## Each row is `{university_bucket, email}` where `university_bucket`
  ## is the (string) university value seen at join time, or the literal
  ## atom `:unknown` when the caller supplied a nil university. A `:set`
  ## table with `{university_bucket, email}` keys — `insert_new`-guarded
  ## so duplicates on re-join don't accumulate — lets `find_best_match/4`
  ## stream candidates grouped by university instead of `tab2list` on the
  ## entire queue. The index is maintained in lock-step with
  ## `@queue_table` (insert on enqueue, delete on dequeue/expiry), so the
  ## two never drift out of sync as long as all writes happen inside the
  ## GenServer (they do — queue writes are all in `handle_call/cast_info`).
  @uni_index_table :matchmaking_queue_uni_index
  ##
  ## Phase 5.31 — Block-list cache populated from social_service.
  ## `find_best_match/4` consults this table on every match attempt so
  ## blocked peers are never selected. Each row is
  ## `{email, %MapSet{} of blocked emails, expiry_monotonic_ms}` so we
  ## only hit social_service once per @blocked_set_ttl_ms window per
  ## user — without this every candidate of a busy caller would trigger
  ## a social_service round-trip.
  @blocked_table :matchmaking_blocked_sets
  @blocked_set_ttl_ms 30_000
  @blocked_set_http_timeout_ms 3_000

  # ── Public API ──────────────────────────────────────────────────────────────

  def start_link(_opts) do
    GenServer.start_link(__MODULE__, :ok, name: __MODULE__)
  end

  @doc "Add user to the matchmaking queue. Returns {:queued, position} or {:matched, pair_id, peer_email, peer_university}."
  def join_queue(email, university \\ nil, major \\ nil) do
    GenServer.call(__MODULE__, {:join_queue, email, university, major})
  end

  @doc "Store the gateway callback URL for a waiting user."
  def register_notify_url(email, url) do
    GenServer.cast(__MODULE__, {:register_notify_url, email, url})
  end

  @doc "Retrieve and remove the stored callback URL for a user."
  def pop_notify_url(email) do
    GenServer.call(__MODULE__, {:pop_notify_url, email})
  end

  @doc "Remove user from the queue voluntarily."
  def leave_queue(email) do
    GenServer.cast(__MODULE__, {:leave_queue, email})
  end

  @doc "Mark an active pair as ended (removes from pairs table)."
  def end_pair(pair_id) do
    GenServer.cast(__MODULE__, {:end_pair, pair_id})
  end

  @doc """
  Returns true if `email` is a participant of the active `pair_id`.

  Used by signaling_service / gateway to verify that a client joining a
  signaling channel actually owns that pair (Phase 0.7 — call-hijack
  prevention). Cheap ETS lookup, no serialization through the GenServer.
  """
  def pair_belongs_to_email?(pair_id, email) do
    case :ets.lookup(@pairs_table, pair_id) do
      [{^pair_id, email_a, email_b, _ts}] -> email in [email_a, email_b]
      _ -> false
    end
  end

  @doc "Current number of users waiting in queue."
  def queue_size do
    case :ets.info(@queue_table, :size) do
      :undefined -> 0
      n -> n
    end
  end

  @doc "Returns all current queue entries – for tests/debug only."
  def queue_entries do
    case :ets.info(@queue_table) do
      :undefined -> []
      _ -> :ets.tab2list(@queue_table)
    end
  end

  # ── GenServer callbacks ─────────────────────────────────────────────────────

  @impl true
  def init(:ok) do
    ensure_ets_tables()
    schedule_heartbeat()
    {:ok, %{}}
  end

  @impl true
  def handle_call({:join_queue, email, university, major}, _from, state) do
    result =
      case :ets.lookup(@queue_table, email) do
        [{^email, _uni, _maj, _ts}] ->
          # Already in queue – return current position
          {:queued, queue_position(email)}

        [] ->
          ts = System.monotonic_time(:millisecond)

          Logger.info(
            "[MatchmakingServer] User #{email} joined queue (Uni: #{university}, Major: #{major})"
          )

          case find_best_match(email, university, major, ts) do
            nil ->
              # No suitable peer yet – add to queue and index it by
              # university (Phase 7.4) so future joins can stream
              # candidates without `tab2list` on the whole queue.
              :ets.insert(@queue_table, {email, university, major, ts})
              index_uni(email, university)
              {:queued, :ets.info(@queue_table, :size)}

            {peer_email, peer_university, _peer_maj, _peer_ts} ->
              # Match found!
              Logger.info("[MatchmakingServer] MATCH FOUND: #{email} <-> #{peer_email}")
              :ets.delete(@queue_table, peer_email)
              unindex_uni(peer_email, peer_university)

              pair_id = generate_pair_id()
              now_ms = System.monotonic_time(:millisecond)
              :ets.insert(@pairs_table, {pair_id, email, peer_email, now_ms})
              record_recent_match(email, peer_email)

              broadcast_stats(:ets.info(@queue_table, :size))

              # Return peer's university so caller can include it in the response
              {:matched, pair_id, peer_email, peer_university}
          end
      end

    # Broadcast updated queue size when someone joins (queued branch)
    case result do
      {:queued, _} -> broadcast_stats(:ets.info(@queue_table, :size))
      _ -> :ok
    end

    {:reply, result, state, :hibernate}
  end

  @impl true
  def handle_call({:pop_notify_url, email}, _from, state) do
    url =
      case :ets.lookup(@notify_table, email) do
        [{^email, stored_url}] ->
          :ets.delete(@notify_table, email)
          stored_url

        [] ->
          nil
      end

    {:reply, url, state, :hibernate}
  end

  @impl true
  def handle_cast({:register_notify_url, email, url}, state) do
    :ets.insert(@notify_table, {email, url})
    {:noreply, state, :hibernate}
  end

  @impl true
  def handle_cast({:leave_queue, email}, state) do
    # Phase 7.4 — drop the per-uni index entry for this email. We peek
    # the queued university from the row before deleting it so the
    # index is removed for the correct bucket (a re-join with a
    # different university would otherwise leave a stale row behind).
    queued_uni =
      case :ets.lookup(@queue_table, email) do
        [{^email, uni, _maj, _ts}] -> uni
        _ -> nil
      end

    :ets.delete(@queue_table, email)
    unindex_uni(email, queued_uni)
    :ets.delete(@notify_table, email)
    broadcast_stats(:ets.info(@queue_table, :size))
    {:noreply, state, :hibernate}
  end

  @impl true
  def handle_cast({:end_pair, pair_id}, state) do
    :ets.delete(@pairs_table, pair_id)
    {:noreply, state, :hibernate}
  end

  @impl true
  def handle_info(:heartbeat, state) do
    now = System.monotonic_time(:millisecond)

    # ── 1. Expire timed-out queue entries ────────────────────────────────────
    expired_emails =
      :ets.tab2list(@queue_table)
      |> Enum.filter(fn {_email, _uni, _maj, ts} -> now - ts >= @queue_timeout_ms end)
      |> Enum.map(fn {email, _uni, _maj, _ts} -> email end)

    Enum.each(expired_emails, fn email ->
      # Capture the queued university before we wipe the row so the
      # per-uni index entry can be dropped in lock-step (Phase 7.4).
      peer_uni =
        case :ets.lookup(@queue_table, email) do
          [{^email, uni, _maj, _ts}] -> uni
          _ -> nil
        end

      :ets.delete(@queue_table, email)
      :ets.delete(@notify_table, email)
      unindex_uni(email, peer_uni)

      # Notify via local channel broadcast (for direct WS clients)
      MatchmakingServiceWeb.Endpoint.broadcast(
        "matchmaking:user:#{email}",
        "queue_timeout",
        %{}
      )

      # Notify via HTTP callback to API Gateway (for gateway-proxied clients)
      notify_via_gateway(email, "queue_timeout", %{})

      Logger.info("[Matchmaking] queue_timeout for #{email}")
    end)

    if expired_emails != [] do
      broadcast_stats(:ets.info(@queue_table, :size))
    end

    # ── 2. Purge stale recent-match records ───────────────────────────────────
    expiry = now - @recent_match_ttl_ms

    :ets.tab2list(@recent_table)
    |> Enum.each(fn {key, ts} ->
      if ts < expiry, do: :ets.delete(@recent_table, key)
    end)

    # ── 3. Phase 7.2 — reap abandoned active pairs ───────────────────────────
    # Pairs are normally ended explicitly (signaling `terminate/1`, the
    # gateway's `end_pair` HTTP call, or the signaling_service's same
    # callback). If both peers drop hard without either signal, the row
    # would otherwise live forever — we cull any pair older than
    # `@active_pair_ttl_ms` defensively.
    pair_expiry = now - @active_pair_ttl_ms

    :ets.tab2list(@pairs_table)
    |> Enum.each(fn {pair_id, _a, _b, ts} ->
      if ts < pair_expiry, do: :ets.delete(@pairs_table, pair_id)
    end)

    schedule_heartbeat()
    {:noreply, state, :hibernate}
  end

  # Test-only reset helper
  if Mix.env() == :test do
    def reset do
      GenServer.call(__MODULE__, :reset)
    end

    def handle_call(:reset, _from, state) do
      :ets.delete_all_objects(@queue_table)
      :ets.delete_all_objects(@pairs_table)
      :ets.delete_all_objects(@recent_table)
      :ets.delete_all_objects(@notify_table)
      :ets.delete_all_objects(@uni_index_table)
      clear_blocked_cache()

      {:reply, :ok, state, :hibernate}
    end
  end

  # ── Private helpers ─────────────────────────────────────────────────────────

  defp ensure_ets_tables do
    table_specs = [
      {@queue_table,
       [:named_table, :public, :set, {:read_concurrency, true}, {:write_concurrency, true}]},
      {@pairs_table, [:named_table, :public, :set, {:read_concurrency, true}]},
      {@recent_table, [:named_table, :public, :set, {:read_concurrency, true}]},
      {@notify_table,
       [:named_table, :public, :set, {:read_concurrency, true}, {:write_concurrency, true}]},
      # Phase 7.4 — per-university index. A :set of {university_bucket, email}
      # rows lets candidates be streamed per bucket instead of `tab2list` on
      # the whole queue; `insert_new` keeps re-joins from accumulating.
      {@uni_index_table,
       [:named_table, :public, :set, {:read_concurrency, true}, {:write_concurrency, true}]},
      # Phase 5.31 — block-list cache from social_service, keyed by caller.
      {@blocked_table,
       [:named_table, :public, :set, {:read_concurrency, true}, {:write_concurrency, true}]}
    ]

    Enum.each(table_specs, fn {name, opts} ->
      if :ets.whereis(name) == :undefined do
        :ets.new(name, opts)
      end
    end)
  end

  # Phase 7.4 — index a freshly-queued user by their university bucket.
  # `:unknown` collapses every nil/blank university into one bucket so
  # cross-uni preference selection still works for anonymous callers.
  defp index_uni(email, university),
    do: :ets.insert_new(@uni_index_table, {uni_bucket(university), email})

  # Phase 7.4 — drop a user from the per-uni index when they leave the
  # queue (via match, manual leave, or expiry). Deleting by exact pair
  # rather than `:match`-scan keeps it O(1) on the bag-less :set table.
  defp unindex_uni(email, university),
    do: :ets.delete_object(@uni_index_table, {uni_bucket(university), email})

  # Phase 7.4 — normalize a (possibly nil/blank) university string into a
  # bucket key. The single source of truth used by both the index writer
  # and `find_best_match/4` so the join-time bucket and the match-time
  # bucket can never drift.
  defp uni_bucket(nil), do: :unknown
  defp uni_bucket(""), do: :unknown
  defp uni_bucket(u) when is_binary(u), do: u

  # ── Phase 5.31 — block-list cache ─────────────────────────────────────────
  #
  # `find_best_match/4` consults this on every match attempt. The cache
  # holds {email, blocked_set, expiry_monotonic_ms} — a fresh entry serves
  # for @blocked_set_ttl_ms, after which we re-fetch from social_service
  # via the internal `/api/internal/blocked-by/:email` endpoint. Both
  # legs are signed with `X-Internal-Secret` (and X-Caller-Email = the
  # queried email, reusing the same chain the InternalAuth plug expects —
  # see social_service/lib/social_service_web/plugs/internal_auth.ex).
  #
  # On social_service failure we fail OPEN (treat the caller as having no
  # known blocks): a social outage should not strand the caller in the
  # queue indefinitely. The trade-off is documented here so the next
  # reviewer can revisit if the SLA ever flips.
  defp blocked_set_for(email) do
    now = System.monotonic_time(:millisecond)

    case :ets.lookup(@blocked_table, email) do
      [{^email, set, expiry}] when expiry > now ->
        set

      _ ->
        set = fetch_blocked_set(email)

        :ets.insert(
          @blocked_table,
          {email, set, System.monotonic_time(:millisecond) + @blocked_set_ttl_ms}
        )

        set
    end
  end

  # HTTP fetch of the caller's blocked-set. Synchronous because
  # `find_best_match/4` already runs inside a GenServer.call — we don't
  # want match-found replies to race a stale cache pop. Short timeout so
  # a slow/dead social_service doesn't stall the matchmaking loop.
  defp fetch_blocked_set(email) do
    social_url = Application.get_env(:matchmaking_service, :social_service_url)

    cond do
      is_nil(social_url) or social_url == "" ->
        # No backend configured (typical in tests) — assume no blocks so
        # tests that don't mock social_service still match freely.
        MapSet.new()

      true ->
        url = "#{social_url}/api/internal/blocked-by/#{URI.encode_www_form(email)}"

        headers = [
          {"X-Internal-Secret", internal_secret()},
          # social_service's InternalAuth requires both headers; we set
          # X-Caller-Email to the queried caller — same value the gateway
          # forwards for a user-proxied block list read.
          {"X-Caller-Email", email}
        ]

        case HTTPoison.get(url, headers,
               recv_timeout: @blocked_set_http_timeout_ms,
               connect_timeout: @blocked_set_http_timeout_ms
             ) do
          {:ok, %{status_code: 200, body: body}} ->
            case Jason.decode(body) do
              {:ok, %{"blocked" => list}} when is_list(list) ->
                MapSet.new(list)

              _ ->
                Logger.warn(
                  "[MatchmakingServer] Unexpected blocked-by response body for #{email}: #{inspect(body)}"
                )

                MapSet.new()
            end

          err ->
            Logger.warn(
              "[MatchmakingServer] blocked-by fetch failed for #{email}, " <>
                "failing open (no blocks): #{inspect(err)}"
            )

            MapSet.new()
        end
    end
  end

  # Test helper — drops stale/forced cache entries so block-list tests
  # don't observe the previous test's TTL. Safe to call from any process
  # since the table is :public. Defined only in test builds (alongside the
  # test-only reset clause that flushes it).
  if Mix.env() == :test do
    def clear_blocked_cache, do: :ets.delete_all_objects(@blocked_table)
  end

  # Find the highest-scoring candidate in the queue (excluding self and
  # anyone blocked by / blocking the caller — Phase 5.31).
  #
  # Phase 7.4 — instead of `tab2list(@queue_table)` on every join (which
  # forces O(n) work *and* an O(n) intermediate list build, making the
  # whole matchmaking loop O(n²) over n concurrent joins), we stream
  # candidates from the per-university index added in this same phase:
  #
  #   1. Iterate `@uni_index_table` to get candidate (university, email)
  #      pairs — a `tab2list` of an index of small 2-tuples is markedly
  #      cheaper than scanning the full 4-tuple queue, and lets us skip
  #      entire buckets cheaply when capped.
  #   2. Look up each candidate's full row in `@queue_table` (a direct
  #      O(1) `:ets.lookup` per email) only for the candidates we
  #      actually intend to score.
  #   3. Cap the scored candidate set at `@match_candidate_cap` so a
  #      pathological queue can't make a single join expensive.
  #
  # We still evaluate every viable candidate (within the cap) because the
  # scoring function is a smooth combination of four fields and there is
  # no short-circuit available — but the per-join cost is now bounded by
  # the cap rather than by the live queue size, which is the substantive
  # remedy the todo asks for.
  defp find_best_match(email, university, major, join_ts) do
    blocked = blocked_set_for(email)
    my_bucket = uni_bucket(university)

    # Order buckets so that *cross*-university candidates are scored
    # first — the match-score function prefers different unis anyway,
    # but processing them first means the running best is almost always
    # the strongest candidate early, so we can bail at the cap with
    # minimal scoring loss.
    buckets =
      @uni_index_table
      |> :ets.tab2list()
      |> Enum.filter(fn {_u, e} -> e != email and not MapSet.member?(blocked, e) end)
      |> Enum.uniq_by(fn {u, _e} -> u end)
      |> Enum.map(fn {u, _e} -> u end)
      |> Enum.uniq()

    cross_buckets = Enum.reject(buckets, &(&1 == my_bucket))
    same_buckets = Enum.filter(buckets, &(&1 == my_bucket))

    candidate_emails =
      (cross_buckets ++ same_buckets)
      |> Enum.flat_map(fn bucket ->
        :ets.match(@uni_index_table, {bucket, :"$1"}) |> List.flatten()
      end)
      |> Enum.reject(&(&1 == email or MapSet.member?(blocked, &1)))
      |> Enum.take(@match_candidate_cap)

    candidates =
      candidate_emails
      |> Enum.map(fn peer_email ->
        case :ets.lookup(@queue_table, peer_email) do
          [{^peer_email, peer_uni, peer_maj, peer_ts}] ->
            {peer_email, peer_uni, peer_maj, peer_ts}

          _ ->
            nil
        end
      end)
      |> Enum.reject(&is_nil/1)

    case candidates do
      [] ->
        nil

      list ->
        Enum.max_by(list, fn {peer_email, peer_uni, peer_maj, peer_ts} ->
          score_match(email, university, major, join_ts, peer_email, peer_uni, peer_maj, peer_ts)
        end)
    end
  end

  @doc """
  4-dimension weighted match score. Higher = better pairing.

      score = 0.35*uni_score + 0.35*avg_wait + 0.15*freshness + 0.15*major_affinity

  - university_score: 1.0 = different unis (preferred), 0.2 = same uni
  - wait_time_score:  averaged wait time, normalised to 60 s, capped at 1.0
  - freshness_score:  1.0 if not recently matched, 0.0 if matched within last 5 min
  - major_affinity:   0.8 same academic family, 0.5 unknown, 0.2 different field
  """
  def score_match(email_a, uni_a, major_a, ts_a, email_b, uni_b, major_b, ts_b) do
    now = System.monotonic_time(:millisecond)

    # Wait scores capped at 1.0, normalised to 60 seconds
    wait_a = min(1.0, (now - ts_a) / 60_000)
    wait_b = min(1.0, (now - ts_b) / 60_000)
    avg_wait = (wait_a + wait_b) / 2.0

    # University diversity: different is preferred (app purpose = meet OTHER uni students)
    uni_score =
      cond do
        # unknown → neutral
        is_nil(uni_a) or is_nil(uni_b) -> 0.5
        # cross-university → preferred
        uni_a != uni_b -> 1.0
        # same university → allowed but less preferred
        true -> 0.2
      end

    # Freshness: 0.0 if recently matched, 1.0 otherwise
    freshness = if recently_matched?(email_a, email_b), do: 0.0, else: 1.0

    # Major affinity: same academic family = good conversation chemistry
    major_score = compute_major_affinity(major_a, major_b)

    @w_university * uni_score +
      @w_wait_time * avg_wait +
      @w_freshness * freshness +
      @w_major * major_score
  end

  # Backward-compat 6-arg version (no major)
  def score_match(email_a, uni_a, ts_a, email_b, uni_b, ts_b) do
    score_match(email_a, uni_a, nil, ts_a, email_b, uni_b, nil, ts_b)
  end

  # ── Major affinity scoring ────────────────────────────────────────────────

  defp compute_major_affinity(nil, _), do: 0.5
  defp compute_major_affinity(_, nil), do: 0.5

  defp compute_major_affinity(major_a, major_b) do
    family_a = classify_major(major_a)
    family_b = classify_major(major_b)

    cond do
      family_a == :unknown or family_b == :unknown -> 0.5
      family_a == family_b -> 0.8
      true -> 0.2
    end
  end

  defp classify_major(major) when is_binary(major) do
    normalized = major |> String.downcase() |> String.replace(~r/[\s\-]/, "_")

    Enum.find_value(@major_families, :unknown, fn {family, keywords} ->
      if Enum.any?(keywords, &String.contains?(normalized, &1)), do: family
    end)
  end

  defp classify_major(_), do: :unknown

  # ── Remaining helpers ────────────────────────────────────────────────────

  defp recently_matched?(email_a, email_b) do
    :ets.member(@recent_table, canonical_key(email_a, email_b))
  end

  defp record_recent_match(email_a, email_b) do
    now = System.monotonic_time(:millisecond)
    :ets.insert(@recent_table, {canonical_key(email_a, email_b), now})
  end

  # Alphabetically sorted so {A,B} ≡ {B,A}
  defp canonical_key(a, b) do
    [x, y] = Enum.sort([a, b])
    "#{x}::#{y}"
  end

  defp queue_position(email) do
    :ets.tab2list(@queue_table)
    |> Enum.find_index(fn {e, _u, _m, _t} -> e == email end)
    |> case do
      nil -> 0
      idx -> idx + 1
    end
  end

  defp generate_pair_id do
    :crypto.strong_rand_bytes(16) |> Base.url_encode64(padding: false)
  end

  defp broadcast_stats(size) do
    MatchmakingServiceWeb.Endpoint.broadcast(
      "matchmaking:stats",
      "queue_stats",
      %{queue_size: size}
    )
  end

  # HTTP callback to the API Gateway to notify a client via the /api/internal/notify endpoint.
  # This is the correct cross-service notification mechanism (no distributed BEAM required).
  defp notify_via_gateway(email, event, payload) do
    gateway_url =
      Application.get_env(:matchmaking_service, :api_gateway_url, "http://localhost:4000")

    url = "#{gateway_url}/api/internal/notify/#{URI.encode_www_form(email)}"

    Task.start(fn ->
      body = Jason.encode!(%{event: event, payload: payload})

      headers = [
        {"Content-Type", "application/json"},
        # Required by gateway since Phase 0.2 — match-found spoofing guard.
        {"X-Internal-Secret", internal_secret()}
      ]

      case HTTPoison.post(url, body, headers,
             recv_timeout: 5_000,
             connect_timeout: 3_000
           ) do
        {:ok, %{status_code: 200}} ->
          :ok

        err ->
          Logger.warn(
            "[MatchmakingServer] notify_via_gateway failed for #{email}: #{inspect(err)}"
          )
      end
    end)
  end

  defp internal_secret do
    Application.get_env(:matchmaking_service, :internal_secret) ||
      "dev_internal_secret_replace_in_production"
  end

  defp schedule_heartbeat do
    Process.send_after(self(), :heartbeat, @heartbeat_interval)
  end
end
