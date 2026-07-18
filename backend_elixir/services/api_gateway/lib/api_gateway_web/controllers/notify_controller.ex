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

  This controller requires an `X-Internal-Secret` header matching the
  shared HMAC between gateway and matchmaking_service. Unauthenticated
  POSTs are rejected (Phase 0.2: previously the route was public,
  allowing anyone to spoof match_found and force a victim's client to
  dial an attacker's peer_email/pair_id).
  """

  use ApiGatewayWeb, :controller
  require Logger

  def notify(conn, %{"email" => email} = params) do
    with [secret | _] <- get_req_header(conn, "x-internal-secret"),
         true <- ApiGateway.AuthBridge.valid_internal_secret?(secret) do
      event = Map.get(params, "event", "match_found")
      payload = Map.get(params, "payload", %{})

      Logger.info("[NotifyController] Broadcasting event=#{event} to user=#{email}")

      ApiGatewayWeb.Endpoint.broadcast(
        "matchmaking:user:#{email}",
        event,
        payload
      )

      json(conn, %{status: "ok"})
    else
      _ ->
        conn
        |> put_status(:unauthorized)
        |> json(%{error: "Internal service secret missing or invalid"})
    end
  end
end
