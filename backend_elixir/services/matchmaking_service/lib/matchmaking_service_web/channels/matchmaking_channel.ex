defmodule MatchmakingServiceWeb.MatchmakingChannel do
  use Phoenix.Channel

  # Phase 8.24 — the @queue_timeout / @heartbeat_interval constants that
  # lived here are removed; the real ones are `@queue_timeout_ms` /
  # `@heartbeat_interval_ms` in `MatchmakingServer` (lib/matchmaking_service/
  # matchmaking_server.ex). The dead module attributes made it look like
  # the channel could tune the heartbeat cadence when it really couldn't.

  alias MatchmakingService.MatchmakingServer

  def join("matchmaking:lobby", _payload, socket) do
    email = socket.assigns.email
    university = socket.assigns.university

    # Subscribe to personal matchmaking channel for notifications
    MatchmakingServiceWeb.Endpoint.subscribe("matchmaking:user:#{email}")

    case MatchmakingServer.join_queue(email, university) do
      {:queued, position} ->
        {:ok, %{status: "queued", position: position}, socket}

      # Immediate match: this socket is the "caller" role. We also stash
      # the pair_id on the socket so `terminate/1` can release the pair if
      # the caller disconnects without an explicit leave (Phase 7.3).
      {:matched, pair_id, peer_email, peer_university} ->
        socket = assign(socket, :pair_id, pair_id)

        {:ok,
         %{
           status: "matched",
           pair_id: pair_id,
           peer_email: peer_email,
           peer_university: peer_university || ""
         }, socket}
    end
  end

  def handle_info(%Phoenix.Socket.Broadcast{event: "matched", payload: payload}, socket) do
    # Phase 7.1 — A peer that was waiting in the queue has just been
    # matched asynchronously (the immediate-match case is handled inside
    # `join/3` above). Record the resulting `pair_id` on the socket so
    # `terminate/1` can release it when this peer disconnects (Phase 7.3).
    socket =
      case payload do
        %{"pair_id" => pair_id} -> assign(socket, :pair_id, pair_id)
        %{pair_id: pair_id} -> assign(socket, :pair_id, pair_id)
        _ -> socket
      end

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
    email = socket.assigns.email

    # Always remove the user from the waiting queue (no-op if already
    # matched out of it).
    MatchmakingServer.leave_queue(email)

    # Phase 7.3 — If this socket was part of an active pair (got matched
    # either immediately in `join/3` or asynchronously via a "matched"
    # broadcast), release the pair so the ETS entry doesn't half-open
    # forever (callers that disconnect without explicitly leaving would
    # otherwise leak in `:active_pairs` until the heartbeat reaper from
    # 7.2 eventually cleans them up).
    case socket.assigns[:pair_id] do
      nil ->
        :ok

      pair_id ->
        MatchmakingServer.end_pair(pair_id)
        :ok
    end
  end
end
