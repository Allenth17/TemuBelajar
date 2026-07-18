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
    #   :set        – Phase 7.6: keyed by the exact {pair_id, email} tuple,
    #                 so reconnects before `terminate`'s `unregister_peer`
    #                 runs no longer accumulate duplicate rows. The old
    #                 :bag table was vulnerable to a network blip turning
    #                 a 2-peer room into a 3-row one and then rejecting
    #                 the genuine second peer with "Room penuh".
    #   :public     – any process (channel process) may read/write
    #   :named_table – accessible by atom name :signaling_peers
    if :ets.whereis(:signaling_peers) == :undefined do
      :ets.new(:signaling_peers, [
        :named_table,
        :public,
        :set,
        {:read_concurrency, true}
      ])
    end

    children = [
      # Phase 7.9 — Task.Supervisor for signaling → matchmaking internal
      # HTTP callbacks. Previously `notify_matchmaking_service_end_pair/1`
      # used a fire-and-forget `Task.start/1` with no timeout and silent
      # error swallowing, so a slow/down matchmaking would cause the
      # `pair_id` to linger forever in `:active_pairs`. The supervisor
      # lets us bound the call via `Task.Supervisor.async_nolink/3` +
      # `Task.yield/2` with a 3s timeout (see `signaling_channel.ex`).
      {Task.Supervisor, name: SignalingService.TaskSupervisor},
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
