defmodule AuthService.Application do
  @moduledoc """
  AuthService OTP Application.

  RAM optimisations:
    - BEAM GC tuned with fullsweep_after: 10 (more aggressive heap collection)
    - Minimal supervisor tree: only what is strictly necessary
  """

  use Application

  @impl true
  def start(_type, _args) do
    # Tune BEAM GC: sweep old-gen heap more aggressively to keep RAM low.
    # This is especially beneficial for long-lived processes like Repo and Endpoint.
    :erlang.system_flag(:fullsweep_after, 10)

    children = [
      AuthServiceWeb.Telemetry,
      AuthService.Repo,
      {Phoenix.PubSub, name: AuthService.PubSub},
      {Finch, name: AuthService.Finch},

      AuthServiceWeb.Endpoint
    ]

    opts = [strategy: :one_for_one, name: AuthService.Supervisor]
    Supervisor.start_link(children, opts)
  end

  @impl true
  def config_change(changed, _new, removed) do
    AuthServiceWeb.Endpoint.config_change(changed, removed)
    :ok
  end
end
