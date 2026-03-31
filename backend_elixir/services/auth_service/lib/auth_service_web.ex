defmodule AuthServiceWeb do
  @moduledoc """
  The entrypoint for defining your web interface, such
  as controllers, components, channels, and so on.

  This can be used in your application as:

      use AuthServiceWeb, :controller
      use AuthServiceWeb, :html

  The definitions below will be executed for every controller,
  component, etc, so keep them short and clean, focused
  on imports, uses and aliases above `import Phoenix.Controller`.
  """

  def static_paths, do: ~w(assets fonts images favicon.ico robots.txt)

  def router do
    quote do
      use Phoenix.Router, helpers: false
      import Plug.Conn
      import Phoenix.Controller
    end
  end

  def channel do
    quote do
      use Phoenix.Channel
    end
  end

  def controller do
    quote do
      use Phoenix.Controller,
        formats: [:json],
        layouts: [html: AuthServiceWeb.Layouts]

      import Plug.Conn
      import Phoenix.Controller
    end
  end

  def verified_routes do
    quote do
      use Phoenix.VerifiedRoutes,
        endpoint: AuthServiceWeb.Endpoint,
        router: AuthServiceWeb.Router,
        signed_in: true
    end
  end

  def view do
    quote do
      use Phoenix.View,
        root: "lib/auth_service_web/templates",
        namespace: AuthServiceWeb

      import Phoenix.Controller,
        only: [get_flash: 1, view_module: 1, view_template: 1]

      unquote(view_helpers())
    end
  end

  def live_view do
    quote do
      use Phoenix.LiveView,
        layout: {AuthServiceWeb.Layouts, :app}

      unquote(view_helpers())
    end
  end

  def live_component do
    quote do
      use Phoenix.LiveComponent

      unquote(view_helpers())
    end
  end

  def component do
    quote do
      use Phoenix.Component

      unquote(view_helpers())
    end
  end

  defp view_helpers do
    quote do
      # Use all HTML functionality (forms, tags, etc)
      use Phoenix.HTML

      # Import LiveView and LiveComponent helpers
      import Phoenix.LiveView.Helpers
      import Phoenix.LiveComponent.Helpers

      # Import basic rendering functions (for, etc)
      import Phoenix.View
    end
  end

  @doc """
  When used, dispatch to the appropriate controller/view/etc.
  """
  defmacro __using__(which) when is_atom(which) do
    apply(__MODULE__, which, [])
  end

  defmacro __using__({:guard, _}) do
    quote do
      import Plug.Conn
      import AuthServiceWeb.Router.Helpers
    end
  end
end
