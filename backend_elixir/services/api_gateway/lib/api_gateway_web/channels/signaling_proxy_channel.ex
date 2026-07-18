defmodule ApiGatewayWeb.SignalingProxyChannel do
  @moduledoc """
  WebSocket channel proxy for WebRTC signaling.

  The gateway itself handles peer tracking via an ETS table so we know
  when BOTH peers have joined before broadcasting "peer_joined".
  All signaling messages (offer, answer, ice_candidate, leave, etc.)
  are relayed with broadcast_from! so the sender does not receive its own
  message back.
  """

  use ApiGatewayWeb, :channel
  require Logger

  alias ApiGateway.AuthBridge

  # Phase 8.10 (unblocks compile) — `terminate/3` (line ~133) and the explicit
  # "leave" handle_in clause both call `notify_matchmaking_end_pair/1`, but the
  # function definition was missing in the worktree (Phase 5.32 stub). Fire a
  # best-effort HTTP POST to matchmaking_service's internal end-pair callback
  # in a separate Task so terminate/3 doesn't block on the network (the channel
  # process is being torn down). Internal-secret header authenticates the
  # call against matchmaking_service's InternalAuth plug.
  defp notify_matchmaking_end_pair(pair_id) when is_binary(pair_id) do
    url = ApiGateway.Services.get_service_url(:matchmaking_service)
    end_pair_url = "#{url}/api/matchmaking/end-pair"

    body = Jason.encode!(%{"pair_id" => pair_id})

    headers = [
      {"Content-Type", "application/json"},
      {"X-Internal-Secret", AuthBridge.internal_secret()}
    ]

    # Bounded HTTP opts — see gateway_controller.ex @downstream_opts. We
    # don't await the result; matchmaking_service logs the failure if any.
    Task.start(fn ->
      HTTPoison.post(end_pair_url, body, headers,
        recv_timeout: 3_000,
        connect_timeout: 2_000
      )
    end)
  end

  @ets_table :gateway_signaling_peers

  # STUN servers sent to clients on join
  @ice_servers [
    %{urls: ["stun:stun.l.google.com:19302"]},
    %{urls: ["stun:stun1.l.google.com:19302"]},
    %{urls: ["stun:stun2.l.google.com:19302"]},
    %{urls: ["stun:stun.cloudflare.com:3478"]}
  ]

  @impl true
  def join("signaling:" <> pair_id, _payload, socket) do
    socket = assign(socket, :pair_id, pair_id)
    peers_count = get_peers_count(pair_id)

    case peers_count do
      0 ->
        register_peer(pair_id, socket.assigns[:email] || socket.assigns[:token])
        send(self(), :send_ice_servers)
        {:ok, %{status: "waiting_for_peer"}, socket}

      1 ->
        register_peer(pair_id, socket.assigns[:email] || socket.assigns[:token])
        send(self(), :send_ice_servers)
        send(self(), :notify_peers_joined)
        {:ok, %{status: "connected"}, socket}

      _ ->
        Logger.warn("[SignalingProxy] Room penuh for pair #{pair_id}")
        {:error, %{reason: "Room penuh"}}
    end
  end

  # ── handle_in callbacks ──────────────────────────────────────────────────────

  @impl true
  def handle_in(event, payload, socket)
      when event in ["offer", "answer", "ice_candidate", "renegotiate"] do
    broadcast_from!(socket, event, payload)
    {:noreply, socket}
  end

  @impl true
  def handle_in(event, _payload, socket) when event in ["leave", "session_end"] do
    broadcast_from!(socket, "peer_left", %{
      reason: "peer_ended_session",
      peer_email: socket.assigns[:email]
    })

    broadcast!(socket, "chat_reset", %{pair_id: socket.assigns.pair_id})

    # Phase 5.32 — explicit graceful leave still tears down the pair on
    # the matchmaking service side; without this the active_pairs ETS row
    # would linger until the 15s/later-defensive heartbeat reaper.
    notify_matchmaking_end_pair(socket.assigns.pair_id)

    {:noreply, socket}
  end

  @impl true
  def handle_in("ping", _payload, socket) do
    {:reply, {:ok, %{pong: true}}, socket}
  end

  @impl true
  def handle_in(_event, _payload, socket) do
    {:noreply, socket}
  end

  # ── handle_info callbacks (all grouped) ─────────────────────────────────────

  @impl true
  def handle_info(:send_ice_servers, socket) do
    push(socket, "ice_servers", %{ice_servers: @ice_servers})
    {:noreply, socket}
  end

  @impl true
  def handle_info(:notify_peers_joined, socket) do
    broadcast!(socket, "peer_joined", %{
      pair_id: socket.assigns.pair_id,
      peer_count: 2
    })

    {:noreply, socket}
  end

  @impl true
  def handle_info(%Phoenix.Socket.Broadcast{event: event, payload: payload}, socket) do
    push(socket, event, payload)
    {:noreply, socket}
  end

  @impl true
  def handle_info(_, socket), do: {:noreply, socket}

  # ── terminate ────────────────────────────────────────────────────────────────
  #
  # Phase 5.32 — when a client disconnects the signaling WS without an
  # explicit "leave" frame (network drop, app hard-quit, browser close),
  # Phoenix calls terminate/3 with reason :normal / :shutdown. We must
  # tell matchmaking_service to end the pair too, otherwise the
  # `:active_pairs` entry lingers until the defensive heartbeat reaper
  # (Phase 7.2) eventually culls it. The HTTP call is fired in a Task so
  # the channel process (which is being torn down) doesn't block on it.
  @impl true
  def terminate(_reason, socket) do
    pair_id = socket.assigns.pair_id

    unregister_peer(pair_id, socket.assigns[:email] || socket.assigns[:token])

    broadcast_from!(socket, "peer_left", %{
      reason: "peer_disconnected",
      peer_email: socket.assigns[:email]
    })

    notify_matchmaking_end_pair(pair_id)

    :ok
  end

  # ── ETS peer tracking ────────────────────────────────────────────────────────

  defp get_peers_count(pair_id) do
    ensure_ets_table()
    :ets.select_count(@ets_table, [{{pair_id, :_}, [], [true]}])
  end

  defp register_peer(pair_id, identifier) do
    ensure_ets_table()
    :ets.insert(@ets_table, {pair_id, identifier})
  end

  defp unregister_peer(pair_id, identifier) do
    ensure_ets_table()
    :ets.delete_object(@ets_table, {pair_id, identifier})
  end

  defp ensure_ets_table do
    case :ets.whereis(@ets_table) do
      :undefined -> :ets.new(@ets_table, [:named_table, :public, :bag])
      _ -> :ok
    end
  end
end
