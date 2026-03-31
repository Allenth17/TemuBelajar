defmodule EmailService.Application do
  # See https://hexdocs.pm/elixir/Application.html
  # for more information on OTP Applications
  @moduledoc """
  EmailService OTP Application.

  RAM optimisations:
    - No Phoenix.PubSub (email service has no channels/sockets)
    - BEAM GC tuned with fullsweep_after: 10
    - Minimal supervisor tree: just the HTTP endpoint
  """
  use Application

  @impl true
  def start(_type, _args) do
    # Tune BEAM GC: sweep old-gen heap more aggressively to keep RAM low.
    :erlang.system_flag(:fullsweep_after, 10)

    children = [
      # HTTP endpoint (uses Bandit, not Cowboy)
      EmailServiceWeb.Endpoint
    ]

    opts = [strategy: :one_for_one, name: EmailService.Supervisor]
    Supervisor.start_link(children, opts)
  end

  @impl true
  def config_change(changed, _new, removed) do
    EmailServiceWeb.Endpoint.config_change(changed, removed)
    :ok
  end

end
