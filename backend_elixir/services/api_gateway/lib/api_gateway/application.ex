defmodule ApiGateway.Application do
  # See https://hexdocs.pm/elixir/Application.html
  # for more information on OTP Applications
  @moduledoc """
  ApiGateway OTP Application.

  RAM optimisations:
    - BEAM GC tuned with fullsweep_after: 10 (more aggressive heap collection)
    - No database (pure HTTP proxy — no Ecto/Repo)
    - Minimal supervisor tree: PubSub + HTTP endpoint
  """

  use Application

  @impl true
  def start(_type, _args) do
    # Tune BEAM GC: sweep old-gen heap more aggressively to keep RAM low.
    :erlang.system_flag(:fullsweep_after, 10)

    # Pre-create ETS table for signaling peer tracking (avoids race in SignalingProxyChannel)
    :ets.new(:gateway_signaling_peers, [:named_table, :public, :bag])

    children = [
      ApiGatewayWeb.Telemetry,
      {Phoenix.PubSub, name: ApiGateway.PubSub},
      ApiGatewayWeb.Endpoint
    ]

    opts = [strategy: :one_for_one, name: ApiGateway.Supervisor]
    Supervisor.start_link(children, opts)
  end

  @impl true
  def config_change(changed, _new, removed) do
    ApiGatewayWeb.Endpoint.config_change(changed, removed)
    :ok
  end
end
