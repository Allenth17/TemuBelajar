defmodule SignalingServiceWeb.Router do
  use SignalingServiceWeb, :router

  pipeline :api do
    plug :accepts, ["json"]
  end

  scope "/api", SignalingServiceWeb do
    pipe_through :api

    # Health check endpoint
    get "/health", HealthController, :health
  end
end
