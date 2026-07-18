defmodule UserServiceWeb.Router do
  use UserServiceWeb, :router

  pipeline :api do
    plug(:accepts, ["json"])
  end

  # All write / read endpoints require the internal-service + caller-email
  # chain established by the gateway (see 0.4 / 0.17). Health remains open.
  pipeline :internal do
    plug(UserServiceWeb.Plugs.InternalAuth)
  end

  scope "/api", UserServiceWeb do
    pipe_through(:api)

    get("/health", HealthController, :health)
  end

  scope "/api", UserServiceWeb do
    pipe_through([:api, :internal])

    # Per-user — caller MUST equal :email (enforced in controller).
    get("/user/:email", UserController, :get_user)
    put("/user/:email", UserController, :update_user)

    # Admin-only — gated by ADMIN_EMAILS env list (see 0.4).
    get("/users", UserController, :list_users)
    get("/users/search", UserController, :search_users)
  end
end
