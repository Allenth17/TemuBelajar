defmodule MatchmakingServiceWeb.Router do
  use MatchmakingServiceWeb, :router

  pipeline :api do
    plug :accepts, ["json"]
  end

  scope "/api", MatchmakingServiceWeb do
    pipe_through :api

    get "/health", HealthController, :health

    post "/matchmaking/join", MatchmakingController, :join
    post "/matchmaking/leave", MatchmakingController, :leave
    post "/matchmaking/end-pair", MatchmakingController, :end_pair
  end
end
