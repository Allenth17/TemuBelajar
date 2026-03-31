defmodule AuthServiceWeb.MatchmakingChannel do
  @moduledoc """
  Phoenix Channel untuk matchmaking.
  Topic: "matchmaking:lobby"
  Events dari client: "join_queue", "leave_queue", "ping"
  Events ke client: "queued", "match_found", "queue_timeout", "queue_stats", "pong"
  """
  use AuthServiceWeb, :channel
  alias AuthService.Realtime.MatchmakingServer
  require Logger

  @impl true
  def join("matchmaking:lobby", _payload, socket) do
    # Subscribe ke personal timeout channel dan stats channel
    Phoenix.PubSub.subscribe(AuthService.PubSub, "matchmaking:#{socket.assigns.email}")
    Phoenix.PubSub.subscribe(AuthService.PubSub, "matchmaking:stats")

    # Push queue size saat join
    queue_size = MatchmakingServer.queue_size()
    {:ok, %{queue_size: queue_size}, socket}
  end

  # ─── Push ke antrian ────────────────────────────────────────────────────────

  @impl true
  def handle_in("join_queue", payload, socket) do
    email      = socket.assigns.email
    university = Map.get(payload, "university")

    case MatchmakingServer.join_queue(email, university) do
      {:queued, position} ->
        {:reply, {:ok, %{status: "queued", position: position}}, socket}

      {:matched, pair_id, peer_email} ->
        # Kita adalah caller (yang menjawab request dari antrian)
        push(socket, "match_found", %{
          pair_id: pair_id,
          role: "caller",
          peer_email: peer_email
        })
        # Broadcast ke peer bahwa dia adalah receiver
        broadcast_match_to_peer(peer_email, pair_id, email)
        {:noreply, socket}
    end
  end

  @impl true
  def handle_in("leave_queue", _payload, socket) do
    MatchmakingServer.leave_queue(socket.assigns.email)
    {:reply, {:ok, %{status: "left"}}, socket}
  end

  @impl true
  def handle_in("ping", _payload, socket) do
    {:reply, {:ok, %{status: "pong", time: DateTime.utc_now() |> DateTime.to_unix(:millisecond)}}, socket}
  end

  # ─── PubSub messages dari server ─────────────────────────────────────────────

  @impl true
  def handle_info({:queue_timeout, _email}, socket) do
    push(socket, "queue_timeout", %{message: "Waktu mencari habis, coba lagi"})
    {:noreply, socket}
  end

  @impl true
  def handle_info({:queue_size, size}, socket) do
    push(socket, "queue_stats", %{queue_size: size})
    {:noreply, socket}
  end

  @impl true
  def handle_info({:match_as_receiver, pair_id, caller_email}, socket) do
    push(socket, "match_found", %{
      pair_id: pair_id,
      role: "receiver",
      peer_email: caller_email
    })
    {:noreply, socket}
  end

  @impl true
  def terminate(_reason, socket) do
    MatchmakingServer.leave_queue(socket.assigns.email)
    :ok
  end

  # ─── Helpers ─────────────────────────────────────────────────────────────────

  defp broadcast_match_to_peer(peer_email, pair_id, caller_email) do
    Phoenix.PubSub.broadcast(
      AuthService.PubSub,
      "matchmaking:#{peer_email}",
      {:match_as_receiver, pair_id, caller_email}
    )
  end
end
