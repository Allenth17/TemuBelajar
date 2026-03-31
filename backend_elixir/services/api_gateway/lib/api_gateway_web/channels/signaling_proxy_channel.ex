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
        Logger.warn("[SignalingProxy] Room full for pair #{pair_id}")
        {:error, %{reason: "Room full"}}
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

  @impl true
  def terminate(_reason, socket) do
    unregister_peer(socket.assigns.pair_id, socket.assigns[:email] || socket.assigns[:token])

    broadcast_from!(socket, "peer_left", %{
      reason: "peer_disconnected",
      peer_email: socket.assigns[:email]
    })

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
