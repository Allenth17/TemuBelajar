defmodule UserServiceWeb.Router do
  use UserServiceWeb, :router

  pipeline :api do
    plug :accepts, ["json"]
  end

  scope "/api", UserServiceWeb do
    pipe_through :api

    get "/health", HealthController, :health

    get "/user/:email", UserController, :get_user
    put "/user/:email", UserController, :update_user
    get "/users", UserController, :list_users
    get "/users/search", UserController, :search_users
  end
end
