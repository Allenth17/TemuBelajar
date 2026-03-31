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
              # No suitable peer yet – add to queue
              :ets.insert(@queue_table, {email, university, major, ts})
              {:queued, :ets.info(@queue_table, :size)}

            {peer_email, peer_university, _peer_maj, _peer_ts} ->
              # Match found!
              Logger.info("[MatchmakingServer] MATCH FOUND: #{email} <-> #{peer_email}")
              :ets.delete(@queue_table, peer_email)

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
    :ets.delete(@queue_table, email)
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
      :ets.delete(@queue_table, email)
      :ets.delete(@notify_table, email)

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
       [:named_table, :public, :set, {:read_concurrency, true}, {:write_concurrency, true}]}
    ]

    Enum.each(table_specs, fn {name, opts} ->
      if :ets.whereis(name) == :undefined do
        :ets.new(name, opts)
      end
    end)
  end

  # Find the highest-scoring candidate in the queue (excluding self).
  defp find_best_match(email, university, major, join_ts) do
    candidates =
      :ets.tab2list(@queue_table)
      |> Enum.reject(fn {e, _u, _m, _t} -> e == email end)

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

      case HTTPoison.post(url, body, [{"Content-Type", "application/json"}], recv_timeout: 5_000) do
        {:ok, %{status_code: 200}} ->
          :ok

        err ->
          Logger.warn(
            "[MatchmakingServer] notify_via_gateway failed for #{email}: #{inspect(err)}"
          )
      end
    end)
  end

  defp schedule_heartbeat do
    Process.send_after(self(), :heartbeat, @heartbeat_interval)
  end
end
