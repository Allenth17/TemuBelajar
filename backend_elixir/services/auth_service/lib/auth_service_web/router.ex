defmodule AuthServiceWeb.Router do
  use Phoenix.Router, helpers: false
  import Plug.Conn

  pipeline :api do
    plug :accepts, ["json"]
  end

  pipeline :auth do
    plug AuthServiceWeb.Plugs.RequireAuth
  end

  scope "/api", AuthServiceWeb do
    pipe_through :api

    # Health check – must be before auth pipeline
    get "/health", HealthController, :health
    # Public auth routes
    post "/register", AuthController, :register
    post "/verify-otp", AuthController, :verify_otp
    post "/resend-otp", AuthController, :resend_otp
    post "/login", AuthController, :login
    # Internal token verification endpoint (called by other microservices)
    get "/verify-token", AuthController, :verify_token

    # Protected routes (require valid Bearer token)
    pipe_through :auth
    post "/logout", AuthController, :logout
    get "/me", AuthController, :me
    delete "/expired-sessions", AuthController, :cleanup_sessions
  end
end
