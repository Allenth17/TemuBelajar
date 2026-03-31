defmodule SocialServiceWeb do
  @moduledoc """
  Entrypoint for defining the web interface of social_service.
  """

  def controller do
    quote do
      use Phoenix.Controller, namespace: SocialServiceWeb

      import Plug.Conn
      alias SocialServiceWeb.Router.Helpers, as: Routes
    end
  end

  def router do
    quote do
      use Phoenix.Router, helpers: false

      import Plug.Conn
      import Phoenix.Controller
    end
  end

  defmacro __using__(which) when is_atom(which) do
    apply(__MODULE__, which, [])
  end
end
