defmodule MatchmakingServiceWeb.MatchmakingController do
  use MatchmakingServiceWeb, :controller
  alias MatchmakingService.MatchmakingServer
  require Logger

  # Join the queue.
  # Body: { "email": "...", "university": "...", "major": "...", "notify_url": "..." }
  # - university and major are optional
  # - notify_url: if provided, the matchmaking service will HTTP-POST to this URL
  #   to notify the WAITING peer when a future match is found asynchronously
  def join(conn, params) do
    email = Map.get(params, "email")
    university = Map.get(params, "university")
    major = Map.get(params, "major")
    notify_url = Map.get(params, "notify_url")

    unless email do
      conn |> put_status(400) |> json(%{error: "email is required"})
    else
      case MatchmakingServer.join_queue(email, university, major) do
        {:queued, position} ->
          # Store notify_url so we can call it when a match is found later
          if notify_url do
            MatchmakingServer.register_notify_url(email, notify_url)
          end

          json(conn, %{status: "queued", position: position})

        {:matched, pair_id, peer_email, peer_university} ->
          # Immediate match: this caller is the "caller" role
          # Also notify the waiting peer via stored notify_url
          notify_peer_async(peer_email, pair_id, email, university)

          json(conn, %{
            status: "matched",
            pair_id: pair_id,
            peer_email: peer_email,
            peer_university: peer_university || ""
          })
      end
    end
  end

  def leave(conn, params) do
    email = Map.get(params, "email")

    unless email do
      conn |> put_status(400) |> json(%{error: "email is required"})
    else
      MatchmakingServer.leave_queue(email)
      json(conn, %{status: "left"})
    end
  end

  def end_pair(conn, params) do
    pair_id = Map.get(params, "pair_id")

    unless pair_id do
      conn |> put_status(400) |> json(%{error: "pair_id is required"})
    else
      MatchmakingServer.end_pair(pair_id)
      json(conn, %{status: "ok"})
    end
  end

  # ── Private ─────────────────────────────────────────────────────────────────

  # Asynchronously notify the WAITING peer that a match was found.
  # We call the gateway's /api/internal/notify/:email endpoint which then
  # broadcasts to the correct WebSocket channel.
  defp notify_peer_async(peer_email, pair_id, caller_email, caller_university) do
    notify_url = MatchmakingServer.pop_notify_url(peer_email)

    if notify_url do
      Task.start(fn ->
        payload = %{
          event: "match_found",
          payload: %{
            pair_id: pair_id,
            peer_email: caller_email,
            peer_university: caller_university || "",
            role: "receiver"
          }
        }

        case HTTPoison.post(
               notify_url,
               Jason.encode!(payload),
               [{"Content-Type", "application/json"}],
               recv_timeout: 5_000
             ) do
          {:ok, %{status_code: 200}} ->
            Logger.info("[MatchmakingController] Notified #{peer_email} at #{notify_url}")

          err ->
            Logger.warn("[MatchmakingController] Failed to notify #{peer_email}: #{inspect(err)}")
        end
      end)
    else
      Logger.warn("[MatchmakingController] No notify_url stored for #{peer_email}")
    end
  end
end
