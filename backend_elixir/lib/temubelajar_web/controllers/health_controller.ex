defmodule TemuBelajarWeb.HealthController do
  use Phoenix.Controller, formats: [:json]

  def index(conn, _params) do
    json(conn, %{message: "backend is running", version: "2.0-elixir"})
  end
end
