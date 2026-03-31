defmodule EmailServiceWeb.HealthController do
  use EmailServiceWeb, :controller

  # GET /api/health
  def health(conn, _params) do
    json(conn, %{status: "ok", service: "email_service"})
  end
end
