defmodule MatchmakingServiceWeb.HealthController do
  use MatchmakingServiceWeb, :controller

  # GET /api/health
  def health(conn, _params) do
    json(conn, %{status: "ok", service: "matchmaking_service"})
  end
end
