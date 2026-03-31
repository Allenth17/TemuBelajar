defmodule TemuBelajarWeb.SignalingChannel do
  @moduledoc """
  Phoenix Channel untuk WebRTC signaling.
  Topic: "signaling:<pair_id>"

  Flow:
  1. Kedua peer join "signaling:<pair_id>"
  2. Server push "ice_servers" saat peer pertama join
  3. Saat peer kedua join → push "peer_joined" ke keduanya
  4. Caller buat offer → push ke server → relay ke receiver
  5. Receiver buat answer → relay ke caller
  6. ICE candidates saling relay
  7. Jika salah satu disconnect/skip → push "peer_left" ke yang lain
  8. "session_end" untuk graceful termination
  """
  use TemuBelajarWeb, :channel
  alias TemuBelajar.Realtime.MatchmakingServer
  require Logger

  # Batas ukuran payload SDP (16KB)
  @max_sdp_size 16_384
  # Batas ukuran ICE candidate
  @max_ice_size 2_048

  # STUN servers yang akan dikirim ke client
  @ice_servers [
    %{urls: ["stun:stun.l.google.com:19302"]},
    %{urls: ["stun:stun1.l.google.com:19302"]},
    %{urls: ["stun:stun2.l.google.com:19302"]},
    %{urls: ["stun:stun.cloudflare.com:3478"]}
  ]

  @impl true
  def join("signaling:" <> pair_id, _payload, socket) do
    socket = assign(socket, :pair_id, pair_id)

    # Track semua user di room ini
    topic = "signaling:#{pair_id}"
    Phoenix.PubSub.subscribe(TemuBelajar.PubSub, topic)

    # Cek berapa peer sudah join
    peers_count = get_peers_count(pair_id)

    case peers_count do
      0 ->
        # Peer pertama — tunggu peer lain
        register_peer(pair_id, socket.assigns.email)
        # Push STUN server config
        send(self(), :send_ice_servers)
        {:ok, %{status: "waiting_for_peer"}, socket}

      1 ->
        # Peer kedua — notify semua
        register_peer(pair_id, socket.assigns.email)
        send(self(), :send_ice_servers)
        send(self(), :notify_peers_joined)
        {:ok, %{status: "connected"}, socket}

      _ ->
        # Room penuh (max 2 peer)
        {:error, %{reason: "Room penuh"}}
    end
  end

  # ─── ICE Servers ─────────────────────────────────────────────────────────────

  @impl true
  def handle_info(:send_ice_servers, socket) do
    push(socket, "ice_servers", %{ice_servers: @ice_servers})
    {:noreply, socket}
  end

  @impl true
  def handle_info(:notify_peers_joined, socket) do
    # Broadcast peer_joined ke semua yang ada di room
    broadcast!(socket, "peer_joined", %{
      pair_id: socket.assigns.pair_id,
      peer_count: 2
    })

    {:noreply, socket}
  end

  # ─── SDP Offer ───────────────────────────────────────────────────────────────

  @impl true
  def handle_in("offer", %{"sdp" => sdp}, socket) when byte_size(sdp) > @max_sdp_size do
    {:reply, {:error, %{reason: "SDP terlalu besar"}}, socket}
  end

  @impl true
  def handle_in("offer", %{"sdp" => sdp}, socket) do
    Logger.debug("Offer dari #{socket.assigns.email} di pair #{socket.assigns.pair_id}")
    broadcast_to_others(socket, "offer", %{sdp: sdp, from: socket.assigns.email})
    {:reply, :ok, socket}
  end

  # ─── SDP Answer ──────────────────────────────────────────────────────────────

  @impl true
  def handle_in("answer", %{"sdp" => sdp}, socket) when byte_size(sdp) > @max_sdp_size do
    {:reply, {:error, %{reason: "SDP terlalu besar"}}, socket}
  end

  @impl true
  def handle_in("answer", %{"sdp" => sdp}, socket) do
    Logger.debug("Answer dari #{socket.assigns.email}")
    broadcast_to_others(socket, "answer", %{sdp: sdp, from: socket.assigns.email})
    {:reply, :ok, socket}
  end

  # ─── ICE Candidate ────────────────────────────────────────────────────────────

  @impl true
  def handle_in("ice_candidate", payload, socket) do
    if byte_size(:erlang.term_to_binary(payload)) > @max_ice_size do
      {:reply, {:error, %{reason: "ICE candidate terlalu besar"}}, socket}
    else
      handle_ice_candidate(payload, socket)
    end
  end

  defp handle_ice_candidate(
         %{"candidate" => candidate, "sdp_mid" => sdp_mid, "sdp_m_line_index" => line_index},
         socket
       ) do
    broadcast_to_others(socket, "ice_candidate", %{
      candidate: candidate,
      sdp_mid: sdp_mid,
      sdp_m_line_index: line_index,
      from: socket.assigns.email
    })

    {:reply, :ok, socket}
  end

  defp handle_ice_candidate(%{"candidate" => candidate}, socket) do
    broadcast_to_others(socket, "ice_candidate", %{
      candidate: candidate,
      from: socket.assigns.email
    })

    {:reply, :ok, socket}
  end

  defp handle_ice_candidate(_payload, socket) do
    {:reply, {:error, %{reason: "Invalid ICE candidate format"}}, socket}
  end

  # ─── Renegotiation (camera switch, etc.) ─────────────────────────────────────

  @impl true
  def handle_in("renegotiate", %{"sdp" => sdp}, socket) do
    broadcast_to_others(socket, "renegotiate", %{sdp: sdp, from: socket.assigns.email})
    {:reply, :ok, socket}
  end

  # ─── Session End (graceful skip/end) ─────────────────────────────────────────

  @impl true
  def handle_in("session_end", _payload, socket) do
    broadcast_to_others(socket, "peer_left", %{
      reason: "peer_ended_session",
      peer_email: socket.assigns.email
    })

    MatchmakingServer.end_pair(socket.assigns.pair_id)
    {:reply, :ok, socket}
  end

  # Also handle the "leave" event sent by the frontend's endSession/nextPerson
  @impl true
  def handle_in("leave", _payload, socket) do
    broadcast_to_others(socket, "peer_left", %{
      reason: "peer_ended_session",
      peer_email: socket.assigns.email
    })

    MatchmakingServer.end_pair(socket.assigns.pair_id)
    {:reply, :ok, socket}
  end

  # ─── Keepalive Ping/Pong ─────────────────────────────────────────────────────

  @impl true
  def handle_in("ping", _payload, socket) do
    {:reply, {:ok, %{pong: true, time: System.monotonic_time(:millisecond)}}, socket}
  end

  # ─── Terminate ───────────────────────────────────────────────────────────────

  @impl true
  def terminate(_reason, socket) do
    unregister_peer(socket.assigns.pair_id, socket.assigns.email)
    # Notify peer lain bahwa pasangan disconnect.
    # broadcast_from! sends to all subscribers except this (already terminating) process.
    broadcast_from!(socket, "peer_left", %{
      reason: "peer_disconnected",
      peer_email: socket.assigns.email
    })

    MatchmakingServer.end_pair(socket.assigns.pair_id)
    :ok
  end

  # ─── Private Helpers ──────────────────────────────────────────────────────────

  # Broadcast ke semua kecuali sender
  defp broadcast_to_others(socket, event, payload) do
    broadcast_from!(socket, event, payload)
  end

  # Track peers dengan ETS (tabel dibuat saat aplikasi start di Application.start/2)

  defp get_peers_count(pair_id) do
    :ets.select_count(:signaling_peers, [{{pair_id, :_}, [], [true]}])
  end

  defp register_peer(pair_id, email) do
    :ets.insert(:signaling_peers, {pair_id, email})
  end

  defp unregister_peer(pair_id, email) do
    :ets.delete_object(:signaling_peers, {pair_id, email})
  end

  @impl true
  def handle_out(event, payload, socket) do
    push(socket, event, payload)
    {:noreply, socket}
  end

  @impl true
  def handle_info(%Phoenix.Socket.Broadcast{event: event, payload: payload}, socket) do
    push(socket, event, payload)
    {:noreply, socket}
  end

  def handle_info(_, socket), do: {:noreply, socket}
end
