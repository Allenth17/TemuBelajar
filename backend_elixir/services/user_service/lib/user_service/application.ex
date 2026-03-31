defmodule UserService.Application do
  @moduledoc """
  UserService OTP Application.

  RAM optimisations:
    - BEAM GC tuned with fullsweep_after: 10 (more aggressive heap collection)
    - No unnecessary PubSub supervisor (user service has no channels/sockets)
    - Minimal supervisor tree: Repo + HTTP endpoint
  """

  use Application

  @impl true
  def start(_type, _args) do
    # Tune BEAM GC: sweep old-gen heap more aggressively to keep RAM low.
    :erlang.system_flag(:fullsweep_after, 10)

    children = [
      UserService.Repo,
      UserServiceWeb.Endpoint
    ]

    opts = [strategy: :one_for_one, name: UserService.Supervisor]
    Supervisor.start_link(children, opts)
  end

  @impl true
  def config_change(changed, _new, removed) do
    UserServiceWeb.Endpoint.config_change(changed, removed)
    :ok
  end
end
