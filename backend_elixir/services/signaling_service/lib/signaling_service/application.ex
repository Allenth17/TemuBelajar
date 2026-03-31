defmodule SignalingService.Application do
  @moduledoc """
  SignalingService OTP Application.

  RAM optimisations:
    - BEAM GC tuned with fullsweep_after: 10 (more aggressive heap collection)
    - :signaling_peers ETS table is created here (owned by the Application
      supervisor process), so it survives individual channel process crashes.
      The channel's own ensure_ets_table/0 becomes a no-op safety fallback.
    - No unnecessary PubSub supervisor beyond what the Endpoint needs.
  """

  use Application

  @impl true
  def start(_type, _args) do
    # Tune BEAM GC: sweep old-gen heap more aggressively.
    # Keeps per-process memory footprint small for many concurrent sockets.
    :erlang.system_flag(:fullsweep_after, 10)

    # Pre-create the ETS table for WebRTC peer tracking.
    # Owned by *this* process (the Application supervisor), so it is never
    # garbage-collected even when all channel processes have exited.
    # Options:
    #   :bag        – a pair_id can have multiple email entries (up to 2 peers)
    #   :public     – any process (channel process) may read/write
    #   :named_table – accessible by atom name :signaling_peers
    if :ets.whereis(:signaling_peers) == :undefined do
      :ets.new(:signaling_peers, [
        :named_table,
        :public,
        :bag,
        {:read_concurrency, true}
      ])
    end
    children = [
      # PubSub is required by Phoenix.Endpoint for channel broadcasting
      {Phoenix.PubSub, name: SignalingService.PubSub},
      # HTTP + WebSocket endpoint (uses Bandit)
      SignalingServiceWeb.Endpoint
    ]

    opts = [strategy: :one_for_one, name: SignalingService.Supervisor]
    Supervisor.start_link(children, opts)
  end

  @impl true
  def config_change(changed, _new, removed) do
    SignalingServiceWeb.Endpoint.config_change(changed, removed)
    :ok
  end
end
