defmodule MatchmakingService.Application do
  @moduledoc false

  use Application

  @impl true
  def start(_type, _args) do
    # Tune BEAM GC: sweep more aggressively to keep per-process heap small.
    # This is especially important for long-lived GenServers that hibernate.
    :erlang.system_flag(:fullsweep_after, 10)

    children = [
      # PubSub is required by Phoenix.Endpoint for channel broadcasting
      {Phoenix.PubSub, name: MatchmakingService.PubSub},
      # HTTP endpoint (uses Bandit)
      MatchmakingServiceWeb.Endpoint,
      # ETS-backed matchmaking queue — must start after Endpoint so it can
      # broadcast via MatchmakingServiceWeb.Endpoint.broadcast/3
      MatchmakingService.MatchmakingServer
    ]

    opts = [strategy: :one_for_one, name: MatchmakingService.Supervisor]
    Supervisor.start_link(children, opts)
  end

  @impl true
  def config_change(changed, _new, removed) do
    MatchmakingServiceWeb.Endpoint.config_change(changed, removed)
    :ok
  end
end
