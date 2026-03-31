defmodule AuthServiceWeb.HealthController do
  use Phoenix.Controller, formats: [:json]

  # GET /api/health
  def health(conn, _params) do
    json(conn, %{status: "ok", service: "auth_service"})
  end
end
