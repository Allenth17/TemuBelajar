defmodule SignalingServiceWeb.HealthController do
  use SignalingServiceWeb, :controller

  # GET /api/health
  def health(conn, _params) do
    json(conn, %{status: "ok", service: "signaling_service"})
  end
end
