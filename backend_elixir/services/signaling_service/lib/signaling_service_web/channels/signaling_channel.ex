defmodule SignalingServiceWeb.SignalingChannel do
  use Phoenix.Channel

  require Logger

  # Batas ukuran payload SDP (16KB)
  @max_sdp_size 16_384
  # Batas ukuran ICE candidate
  @max_ice_size 2_048
  # Heartbeat interval
  @heartbeat_interval 30_000
  #
  ## Phase 7.11 — No-answer timeout. After peer A sends an offer we
  ## schedule this timer; if no answer broadcast arrives before it
  ## fires (typically because peer B disconnected before noticing the
  ## offer, or never answered) we tell A the peer is unresponsive so A
  ## can return to matchmaking instead of waiting forever. The timer is
  ## per-socket (per-A), started in the offer handler and cancelled when
  ## A's own channel process receives the "answer" PubSub broadcast (see
  ## `handle_info/2` below).
  @no_answer_timeout_ms 30_000

  # STUN servers yang akan dikirim ke client
  @ice_servers [
    %{urls: ["stun:stun.l.google.com:19302"]},
    %{urls: ["stun:stun1.l.google.com:19302"]},
    %{urls: ["stun:stun2.l.google.com:19302"]},
    %{urls: ["stun:stun.cloudflare.com:3478"]}
  ]

  # Phase 0.7 — verify pair ownership BEFORE registering the peer, so a
  # third party enumerating/guessing pair_ids can't join an in-progress
  # call or snoop SDP. The auth_service-validates :email is already set
  # in the socket by SignalingServiceWeb.UserSocket.connect/3.
  def join("signaling:" <> pair_id, _payload, socket) do
    email = socket.assigns[:email]

    cond do
      is_nil(email) ->
        {:error, %{reason: "Unauthenticated"}}

      not verify_pair_ownership?(pair_id, email) ->
        Logger.warn("[SignalingChannel] Reject join pair=#{pair_id} email=#{email} — not owner")
        {:error, %{reason: "Pair not found or not owned by caller"}}

      true ->
        socket = assign(socket, :pair_id, pair_id)
        # Phase 7.7 — Use a nil sentinel, not `DateTime.utc_now()`. The
        # old code set `last_heartbeat` to `now()` at join, so the first
        # heartbeat check at +30s always passed even if the client never
        # heartbeated. With nil the first check forces an immediate
        # disconnect unless the client has already sent at least one
        # heartbeat, which is what we want.
        socket = assign(socket, :last_heartbeat, nil)
        socket = assign(socket, :registered?, true)

        peers_count = get_peers_count(pair_id)

        case peers_count do
          0 ->
            # Peer pertama — tunggu peer lain
            register_peer(pair_id, email)
            send(self(), :send_ice_servers)
            send(self(), :heartbeat_check)
            {:ok, %{status: "waiting_for_peer"}, socket}

          1 ->
            # Peer kedua — notify semua
            register_peer(pair_id, email)
            send(self(), :send_ice_servers)
            send(self(), :notify_peers_joined)
            send(self(), :heartbeat_check)
            {:ok, %{status: "connected"}, socket}

          _ ->
            # Room penuh (max 2 peer)
            {:error, %{reason: "Room penuh"}}
        end
    end
  end

  def handle_info(:heartbeat_check, socket) do
    now = DateTime.utc_now()
    last_heartbeat = socket.assigns[:last_heartbeat]

    # Phase 7.7 — `last_heartbeat` starts as nil at join. A client that
    # never sends a heartbeat is therefore disconnected on the first
    # check (after `@heartbeat_interval`) instead of being given a free
    # pass that lets a silent socket hold a peer slot for up to 60s.
    timed_out? =
      case last_heartbeat do
        nil ->
          true

        dt when is_struct(dt, DateTime) ->
          DateTime.diff(now, dt, :millisecond) > @heartbeat_interval
      end

    if timed_out? do
      # No heartbeat received, disconnect
      Logger.info(
        "Peer #{socket.assigns.email} in pair #{socket.assigns.pair_id} timed out due to no heartbeat."
      )

      {:stop, :normal, socket}
    else
      # Schedule next heartbeat check
      Process.send_after(self(), :heartbeat_check, @heartbeat_interval)
      {:noreply, socket}
    end
  end

  def handle_info(:send_ice_servers, socket) do
    push(socket, "ice_servers", %{ice_servers: @ice_servers})
    {:noreply, socket}
  end

  def handle_info(:notify_peers_joined, socket) do
    broadcast!(socket, "peer_joined", %{
      pair_id: socket.assigns.pair_id,
      peer_count: 2
    })

    {:noreply, socket}
  end

  # Phase 7.11 — Intercept the "answer" PubSub broadcast in the offering
  # peer's channel process so we can cancel the no-answer timer. This
  # handler runs *in addition to* the inbound reply path; Phoenix's
  # default `handle_info/2` for a Broadcast would push the event as-is,
  # but we want to be sure the timer is cleared. We push the answer to
  # our own client (the offering peer receives the remote SDP) and drop
  # the timer.
  def handle_info(%Phoenix.Socket.Broadcast{event: "answer", payload: payload}, socket) do
    socket = cancel_no_answer_check(socket)
    push(socket, "answer", payload)
    {:noreply, socket}
  end

  # Phase 7.11 — Timer fired before any "answer" broadcast arrived.
  # Tell the offering peer the remote side is unresponsive so the client
  # can return to matchmaking instead of waiting indefinitely.
  def handle_info(:no_answer_check, socket) do
    if socket.assigns[:no_answer_timer_ref] do
      Logger.info(
        "[SignalingChannel] No-answer timeout for #{socket.assigns.email} in pair #{socket.assigns.pair_id}"
      )

      push(socket, "peer_unresponsive", %{pair_id: socket.assigns.pair_id})
      socket = assign(socket, :no_answer_timer_ref, nil)
      {:noreply, socket}
    else
      # Timer already cancelled (answer arrived or this is a stray) — no-op.
      {:noreply, socket}
    end
  end

  def handle_in("heartbeat", _payload, socket) do
    socket = assign(socket, :last_heartbeat, DateTime.utc_now())
    {:noreply, socket}
  end

  # Phase 7.10 — Guard non-binary SDP payloads explicitly, before the
  # `byte_size/1` guard runs (which would raise `FunctionClauseError` on
  # e.g. `{"sdp": 123}`).
  # Phase 3.18 — error message aligned to Indonesian across services.
  def handle_in("offer", %{"sdp" => sdp}, socket) when not is_binary(sdp) do
    {:reply, {:error, %{reason: "SDP tidak valid"}}, socket}
  end

  def handle_in("offer", %{"sdp" => sdp}, socket) when byte_size(sdp) > @max_sdp_size do
    {:reply, {:error, %{reason: "SDP terlalu besar"}}, socket}
  end

  def handle_in("offer", %{"sdp" => sdp}, socket) do
    # Phase 7.11 — Start the no-answer timer in this socket's process.
    # If the peer's "answer" broadcast arrives before the timer fires,
    # `handle_info/2` cancels it; otherwise we push `peer_unresponsive`.
    socket = schedule_no_answer_check(socket)

    broadcast_from!(socket, "offer", %{sdp: sdp, from: socket.assigns.email})
    {:noreply, socket}
  end

  def handle_in("answer", %{"sdp" => sdp}, socket) when not is_binary(sdp) do
    {:reply, {:error, %{reason: "SDP tidak valid"}}, socket}
  end

  def handle_in("answer", %{"sdp" => sdp}, socket) when byte_size(sdp) > @max_sdp_size do
    {:reply, {:error, %{reason: "SDP terlalu besar"}}, socket}
  end

  def handle_in("answer", %{"sdp" => sdp}, socket) do
    broadcast_from!(socket, "answer", %{sdp: sdp, from: socket.assigns.email})
    {:noreply, socket}
  end

  def handle_in("ice_candidate", payload, socket) do
    if byte_size(:erlang.term_to_binary(payload)) > @max_ice_size do
      {:reply, {:error, %{reason: "ICE candidate terlalu besar"}}, socket}
    else
      broadcast_from!(socket, "ice_candidate", Map.put(payload, "from", socket.assigns.email))
      {:noreply, socket}
    end
  end

  def handle_in("leave", _payload, socket) do
    broadcast_from!(socket, "peer_left", %{
      reason: "peer_ended_session",
      peer_email: socket.assigns.email
    })

    # Reset chat on both sides when peer presses "Next"
    broadcast!(socket, "chat_reset", %{
      reason: "peer_left",
      pair_id: socket.assigns.pair_id
    })

    notify_matchmaking_service_end_pair(socket.assigns.pair_id)
    {:noreply, socket}
  end

  def terminate(_reason, socket) do
    # Phase 7.8 — Only run cleanup if we actually registered as a peer on
    # this pair. The reject paths (`Unauthenticated`, pair-not-owned,
    # `Room penuh`) all return `{:error, ...}` *before* they assign
    # `:registered?`, so for them `terminate/1` must be a no-op —
    # otherwise the rejected socket would broadcast a spurious `peer_left`
    # / `chat_reset` to a pair it never joined and call the matchmaking
    # `end-pair` against a pair it doesn't own.
    if socket.assigns[:registered?] do
      unregister_peer(socket.assigns.pair_id, socket.assigns.email)

      broadcast_from!(socket, "peer_left", %{
        reason: "peer_disconnected",
        peer_email: socket.assigns.email
      })

      # Also reset chat when peer disconnects unexpectedly
      broadcast_from!(socket, "chat_reset", %{
        reason: "peer_disconnected",
        pair_id: socket.assigns[:pair_id]
      })

      notify_matchmaking_service_end_pair(socket.assigns.pair_id)
    end

    :ok
  end

  # ─── Private Helpers ──────────────────────────────────────────────────────────

  # Phase 8.10 (unblocks compile) — `@no_answer_timeout_ms` references
  # fulfil Phase 7.11: schedule a :no_answer_check timer in this socket's
  # process so `handle_info(:no_answer_check, socket)` above fires when
  # the answer broadcast hasn't come back. The ref is stored on the socket
  # for cancellation in the `answer` broadcast handler.
  defp schedule_no_answer_check(socket) do
    ref = Process.send_after(self(), :no_answer_check, @no_answer_timeout_ms)
    assign(socket, :no_answer_timer_ref, ref)
  end

  defp cancel_no_answer_check(socket) do
    case socket.assigns[:no_answer_timer_ref] do
      nil ->
        socket

      ref when is_reference(ref) ->
        Process.cancel_timer(ref)
        assign(socket, :no_answer_timer_ref, nil)
    end
  end

  defp get_peers_count(pair_id) do
    ensure_ets_table()
    # Phase 7.6 — table is now a :set keyed on {pair_id, email}, so each
    # (pair_id, email) row appears at most once even across reconnects.
    # `select_count` over `pair_id` therefore equals the count of distinct
    # emails currently registered for this pair.
    :ets.select_count(:signaling_peers, [{{pair_id, :_}, [], [true]}])
  end

  defp register_peer(pair_id, email) do
    ensure_ets_table()
    # Phase 7.6 — `:set` table + exact-key insert is idempotent: a peer
    # that reconnects after a transient drop (before its old channel
    # process ran `terminate`) replaces its own row instead of
    # accumulating a duplicate. Without this the room would soon read
    # "Room penuh" against a single genuine peer.
    :ets.insert(:signaling_peers, {pair_id, email})
  end

  defp unregister_peer(pair_id, email) do
    ensure_ets_table()
    :ets.delete_object(:signaling_peers, {pair_id, email})
  end

  defp ensure_ets_table do
    case :ets.whereis(:signaling_peers) do
      :undefined -> :ets.new(:signaling_peers, [:named_table, :public, :set])
      _ -> :ok
    end
  end

  # Phase 7.9 — Notify matchmaking that `pair_id` has ended.
  #
  # Switched from `Task.start/1` (fire-and-forget, no timeout, errors
  # swallowed) to `Task.Supervisor.async_nolink/3` so that:
  #
  #   • the call is bounded by a 3s timeout (the recv/connect timeout is
  #     1500 ms per attempt, the yield window is 3000 ms total);
  #   • a crash in matchmaking doesn't take down this channel process
  #     (the supervisor creates the task under `:no_link`);
  #   • one retry is attempted on failure so a transient matchmaking
  #     blip doesn't strand the pair in `:active_pairs` until the
  #     matchmaking heartbeat reaper (Phase 7.2) eventually cleans it up.
  #
  # The async-yield pattern keeps `terminate/1` non-blocking: we kick
  # off the work and only *briefly* wait on it. If the task outlives the
  # yield, we shut it down so we don't leak a process.
  defp notify_matchmaking_service_end_pair(pair_id) do
    matchmaking_url = Application.get_env(:signaling_service, :matchmaking_service_url)

    if matchmaking_url do
      endpoint = "#{matchmaking_url}/api/matchmaking/end-pair"

      attempt = fn ->
        headers = [
          {"Content-Type", "application/json"},
          {"X-Internal-Secret", internal_secret()}
        ]

        case HTTPoison.post(
               endpoint,
               Jason.encode!(%{pair_id: pair_id}),
               headers,
               recv_timeout: 1_500,
               connect_timeout: 1_500
             ) do
          {:ok, %HTTPoison.Response{status_code: status}} when status in 200..299 ->
            :ok

          err ->
            Logger.warn("[SignalingChannel] end-pair POST failed for #{pair_id}: #{inspect(err)}")

            :error
        end
      end

      run_with_timeout = fn ->
        task = Task.Supervisor.async_nolink(SignalingService.TaskSupervisor, attempt)

        case Task.yield(task, 3_000) do
          {:ok, :ok} ->
            :ok

          {:ok, :error} ->
            :retry

          nil ->
            # Timed out — synchronously shut the task down so we don't
            # leak a process that outlives the yield window.
            Task.shutdown(task, :brutal_kill)
            :retry
        end
      end

      # First attempt
      case run_with_timeout.() do
        :ok ->
          :ok

        :retry ->
          # Single retry after the (very short) timeout above.
          case run_with_timeout.() do
            :ok -> :ok
            _ -> Logger.warn("[SignalingChannel] end-pair retry exhausted for #{pair_id}")
          end
      end
    end
  end

  # Verify with matchmaking that (email, pair_id) belongs to an active pair.
  # Done via HTTP with a short timeout so a slow/down matchmaking service
  # doesn't hang the channel join (Phase 0.7 + 3.22). Public so the
  # chat_channel can reuse it without duplicating the HTTP call.
  def verify_pair_ownership?(pair_id, email) when is_binary(pair_id) and is_binary(email) do
    matchmaking_url = Application.get_env(:signaling_service, :matchmaking_service_url)

    if is_nil(matchmaking_url) do
      # No matchmaking URL configured — fail closed.
      false
    else
      url = "#{matchmaking_url}/api/matchmaking/verify-pair"
      body = Jason.encode!(%{pair_id: pair_id, email: email})

      headers = [
        {"Content-Type", "application/json"},
        {"X-Internal-Secret", internal_secret()}
      ]

      case HTTPoison.post(url, body, headers, recv_timeout: 2_000, connect_timeout: 2_000) do
        {:ok, %HTTPoison.Response{status_code: 200, body: resp_body}} ->
          case Jason.decode(resp_body) do
            {:ok, %{"valid" => true}} -> true
            _ -> false
          end

        _ ->
          # Fail closed on any error/timeout — never let an unverifiable
          # pair through; that's the whole point of 0.7.
          false
      end
    end
  end

  defp internal_secret do
    Application.get_env(:signaling_service, :internal_secret) ||
      "dev_internal_secret_replace_in_production"
  end
end
