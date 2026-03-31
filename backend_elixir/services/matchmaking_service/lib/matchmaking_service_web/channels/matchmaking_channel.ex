defmodule MatchmakingServiceWeb.MatchmakingChannel do
  use Phoenix.Channel

  @queue_timeout 60_000  # 60 seconds
  @heartbeat_interval 30_000  # 30 seconds

  alias MatchmakingService.MatchmakingServer

  def join("matchmaking:lobby", _payload, socket) do
    email = socket.assigns.email
    university = socket.assigns.university

    # Subscribe to personal matchmaking channel for notifications
    MatchmakingServiceWeb.Endpoint.subscribe("matchmaking:user:#{email}")

    case MatchmakingServer.join_queue(email, university) do
      {:queued, position} ->
        {:ok, %{status: "queued", position: position}, socket}

      {:matched, pair_id, peer_email} ->
        {:ok, %{status: "matched", pair_id: pair_id, peer_email: peer_email}, socket}
    end
  end

  def handle_info(%Phoenix.Socket.Broadcast{event: "matched", payload: payload}, socket) do
    push(socket, "matched", payload)
    {:noreply, socket}
  end

  def handle_info(%Phoenix.Socket.Broadcast{event: "queue_timeout"}, socket) do
    push(socket, "queue_timeout", %{})
    {:noreply, socket}
  end

  def handle_in("leave", _payload, socket) do
    MatchmakingServer.leave_queue(socket.assigns.email)
    {:stop, :normal, socket}
  end

  def terminate(_reason, socket) do
    MatchmakingServer.leave_queue(socket.assigns.email)
    :ok
  end
end
