defmodule TemuBelajarWeb.Router do
  use Phoenix.Router, helpers: false
  import Plug.Conn

  pipeline :api do
    plug :accepts, ["json"]
  end

  pipeline :auth do
    plug TemuBelajarWeb.Plugs.RequireAuth
  end

  scope "/api", TemuBelajarWeb do
    pipe_through :api

    # Public auth routes
    post "/register",    AuthController, :register
    post "/verify-otp",  AuthController, :verify_otp
    post "/resend-otp",  AuthController, :resend_otp
    post "/login",       AuthController, :login

    # Protected routes
    pipe_through :auth
    post "/logout",      AuthController, :logout
    get  "/me",          AuthController, :me
    delete "/expired-sessions", AuthController, :cleanup_sessions
  end

  # Health check
  scope "/", TemuBelajarWeb do
    pipe_through :api
    get "/", HealthController, :index
  end
end
