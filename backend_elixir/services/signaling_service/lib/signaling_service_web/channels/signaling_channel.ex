defmodule SignalingServiceWeb.SignalingChannel do
  use Phoenix.Channel

  require Logger

  # Batas ukuran payload SDP (16KB)
  @max_sdp_size 16_384
  # Batas ukuran ICE candidate
  @max_ice_size 2_048
  # Heartbeat interval
  @heartbeat_interval 30_000

  # STUN servers yang akan dikirim ke client
  @ice_servers [
    %{urls: ["stun:stun.l.google.com:19302"]},
    %{urls: ["stun:stun1.l.google.com:19302"]},
    %{urls: ["stun:stun2.l.google.com:19302"]},
    %{urls: ["stun:stun.cloudflare.com:3478"]}
  ]

  def join("signaling:" <> pair_id, _payload, socket) do
    socket = assign(socket, :pair_id, pair_id)
    socket = assign(socket, :last_heartbeat, DateTime.utc_now())

    # Cek berapa peer sudah join
    peers_count = get_peers_count(pair_id)

    case peers_count do
      0 ->
        # Peer pertama — tunggu peer lain
        register_peer(pair_id, socket.assigns.email)
        # Push STUN server config
        send(self(), :send_ice_servers)
        send(self(), :heartbeat_check)
        {:ok, %{status: "waiting_for_peer"}, socket}

      1 ->
        # Peer kedua — notify semua
        register_peer(pair_id, socket.assigns.email)
        send(self(), :send_ice_servers)
        send(self(), :notify_peers_joined)
        send(self(), :heartbeat_check)
        {:ok, %{status: "connected"}, socket}

      _ ->
        # Room penuh (max 2 peer)
        {:error, %{reason: "Room penuh"}}
    end
  end

  def handle_info(:heartbeat_check, socket) do
    now = DateTime.utc_now()
    last_heartbeat = socket.assigns[:last_heartbeat]

    if DateTime.diff(now, last_heartbeat, :millisecond) > @heartbeat_interval do
      # No heartbeat received, disconnect
      Logger.info("Peer #{socket.assigns.email} in pair #{socket.assigns.pair_id} timed out due to no heartbeat.")
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

  def handle_in("heartbeat", _payload, socket) do
    socket = assign(socket, :last_heartbeat, DateTime.utc_now())
    {:noreply, socket}
  end

  def handle_in("offer", %{"sdp" => sdp}, socket) when byte_size(sdp) > @max_sdp_size do
    {:reply, {:error, %{reason: "SDP terlalu besar"}}, socket}
  end

  def handle_in("offer", %{"sdp" => sdp}, socket) do
    broadcast_from!(socket, "offer", %{sdp: sdp, from: socket.assigns.email})
    {:noreply, socket}
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
    :ok
  end

  # ─── Private Helpers ──────────────────────────────────────────────────────────

  defp get_peers_count(pair_id) do
    ensure_ets_table()
    :ets.select_count(:signaling_peers, [{{pair_id, :_}, [], [true]}])
  end

  defp register_peer(pair_id, email) do
    ensure_ets_table()
    :ets.insert(:signaling_peers, {pair_id, email})
  end

  defp unregister_peer(pair_id, email) do
    ensure_ets_table()
    :ets.delete_object(:signaling_peers, {pair_id, email})
  end

  defp ensure_ets_table do
    case :ets.whereis(:signaling_peers) do
      :undefined -> :ets.new(:signaling_peers, [:named_table, :public, :bag])
      _ -> :ok
    end
  end

  defp notify_matchmaking_service_end_pair(pair_id) do
    # In microservices, we call the matchmaking service via HTTP or PubSub.
    # We'll use HTTP for now.
    matchmaking_url = Application.get_env(:signaling_service, :matchmaking_service_url)
    if matchmaking_url do
      Task.start(fn ->
        HTTPoison.post("#{matchmaking_url}/api/matchmaking/end-pair", Jason.encode!(%{pair_id: pair_id}), [{"Content-Type", "application/json"}])
      end)
    end
  end
end
