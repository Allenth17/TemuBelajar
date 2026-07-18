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
          # Immediate match: this caller is the "caller" role.
          #
          # Phase 7.1 — Always broadcast the matched event on the waiting
          # peer's personal PubSub topic (`matchmaking:user:<peer_email>`)
          # regardless of whether a `notify_url` was registered. A caller
          # that joined directly over WebSocket never registers a
          # `notify_url` (that path is only used by gateway-proxied HTTP
          # clients), so previously such peers would never learn they had
          # been matched and would time out after 90 s.
          #
          # The WS path subscribes to its personal topic in
          # `MatchmakingChannel.join/3`, so the broadcast reaches it
          # immediately. For gateway-proxied clients we *also* keep the
          # HTTP callback (below) since they are not subscribed to the
          # local PubSub topic from this node.
          MatchmakingServiceWeb.Endpoint.broadcast!(
            "matchmaking:user:#{peer_email}",
            "matched",
            %{
              pair_id: pair_id,
              peer_email: email,
              peer_university: university || "",
              role: "receiver"
            }
          )

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

  # POST /api/matchmaking/verify-pair — internal-only. Body: {pair_id, email}.
  # Returns %{valid: bool} so the caller can gate channel join on it.
  def verify_pair(conn, %{"pair_id" => pair_id, "email" => email}) do
    case MatchmakingServer.pair_belongs_to_email?(pair_id, email) do
      true -> json(conn, %{valid: true})
      _ -> conn |> put_status(403) |> json(%{valid: false})
    end
  end

  def verify_pair(conn, _params) do
    conn |> put_status(400) |> json(%{error: "pair_id and email are required"})
  end

  # ── Private ─────────────────────────────────────────────────────────────────

  # Asynchronously notify the WAITING peer that a match was found.
  # We call the gateway's /api/internal/notify/:email endpoint which then
  # broadcasts to the correct WebSocket channel.
  #
  # Phase 7.1 — Only gateway-proxied HTTP clients register a `notify_url`;
  # direct WS clients are reached via the PubSub broadcast emitted in
  # `join/2`. The absence of a `notify_url` therefore only means there is
  # nothing to do over HTTP, not that the peer was unreachable.
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

        headers = [
          {"Content-Type", "application/json"},
          # Required since Phase 0.2 — the gateway rejects unsigned internal
          # notify POSTs to prevent match-found spoofing.
          {"X-Internal-Secret", internal_secret()}
        ]

        case HTTPoison.post(
               notify_url,
               Jason.encode!(payload),
               headers,
               recv_timeout: 5_000,
               connect_timeout: 3_000
             ) do
          {:ok, %{status_code: 200}} ->
            Logger.info("[MatchmakingController] Notified #{peer_email} at #{notify_url}")

          err ->
            Logger.warn("[MatchmakingController] Failed to notify #{peer_email}: #{inspect(err)}")
        end
      end)
    else
      # No HTTP callback registered — the peer is reachable via the
      # PubSub broadcast we already emitted in `join/2`. This is the
      # common case for direct-WS clients, so we only log at debug.
      Logger.debug(
        "[MatchmakingController] No notify_url for #{peer_email} — relying on PubSub broadcast"
      )
    end
  end

  defp internal_secret do
    Application.get_env(:matchmaking_service, :internal_secret) ||
      "dev_internal_secret_replace_in_production"
  end
end
