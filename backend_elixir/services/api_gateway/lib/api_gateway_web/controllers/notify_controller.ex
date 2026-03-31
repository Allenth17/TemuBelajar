defmodule ApiGatewayWeb.NotifyController do
  @moduledoc """
  Internal HTTP endpoint called by other microservices to push real-time
  notifications to connected WebSocket clients.

  POST /api/internal/notify/:email
  Body (JSON):
    {
      "event":   "match_found" | "queue_timeout" | "queue_stats",
      "payload": { ... }
    }

  This controller broadcasts the event+payload to "matchmaking:user:<email>"
  which is subscribed by MatchmakingProxyChannel for the given user.
  """

  use ApiGatewayWeb, :controller
  require Logger

  def notify(conn, %{"email" => email} = params) do
    event = Map.get(params, "event", "match_found")
    payload = Map.get(params, "payload", %{})

    Logger.info("[NotifyController] Broadcasting event=#{event} to user=#{email}")

    ApiGatewayWeb.Endpoint.broadcast(
      "matchmaking:user:#{email}",
      event,
      payload
    )

    json(conn, %{status: "ok"})
  end
end
