defmodule SocialServiceWeb.HealthController do
  use SocialServiceWeb, :controller

  def index(conn, _params) do
    json(conn, %{status: "ok", service: "social_service"})
  end
end
