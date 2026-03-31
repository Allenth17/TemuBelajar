defmodule EmailServiceWeb.Router do
  use EmailServiceWeb, :router

  pipeline :api do
    plug :accepts, ["json"]
  end

  scope "/api", EmailServiceWeb do
    pipe_through :api

    get "/health", HealthController, :health
    post "/send-otp", EmailController, :send_otp
  end
end
