defmodule UserServiceWeb.HealthController do
  use UserServiceWeb, :controller

  # GET /api/health
  def health(conn, _params) do
    json(conn, %{status: "ok", service: "user_service"})
  end
end
