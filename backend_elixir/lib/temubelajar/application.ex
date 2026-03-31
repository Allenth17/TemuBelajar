defmodule TemuBelajar.Application do
  @moduledoc false
  use Application

  @impl true
  def start(_type, _args) do
    # Pre-create ETS table for signaling peer tracking to avoid race conditions
    # in SignalingChannel when two peers join simultaneously
    :ets.new(:signaling_peers, [:named_table, :public, :bag])

    children = [
      TemuBelajarWeb.Telemetry,
      TemuBelajar.Repo,
      {DNSCluster, query: Application.get_env(:temubelajar, :dns_cluster_query) || :ignore},
      {Phoenix.PubSub, name: TemuBelajar.PubSub},
      {Finch, name: TemuBelajar.Finch},
      # Matchmaking GenServer — antrean user menunggu pasangan
      TemuBelajar.Realtime.MatchmakingServer,
      TemuBelajarWeb.Endpoint
    ]

    opts = [strategy: :one_for_one, name: TemuBelajar.Supervisor]
    Supervisor.start_link(children, opts)
  end

  @impl true
  def config_change(changed, _new, removed) do
    TemuBelajarWeb.Endpoint.config_change(changed, removed)
    :ok
  end
end
