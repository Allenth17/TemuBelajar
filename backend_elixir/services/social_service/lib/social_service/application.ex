defmodule SocialService.Application do
  @moduledoc """
  Social Service OTP Application.

  Handles follow/friend/block/report social graph.

  RAM optimisations:
    - BEAM GC: fullsweep_after 10 (aggressive heap reclaim)
    - ETS-based follower count cache (shared, O(1) reads)
    - Minimal supervisor: Repo + Endpoint + EtsCache
    - No PubSub (social service is stateless HTTP only)
  """

  use Application

  @impl true
  def start(_type, _args) do
    :erlang.system_flag(:fullsweep_after, 10)

    children = [
      SocialService.Repo,
      SocialService.FollowerCache,
      SocialServiceWeb.Endpoint
    ]

    opts = [strategy: :one_for_one, name: SocialService.Supervisor]
    Supervisor.start_link(children, opts)
  end

  @impl true
  def config_change(changed, _new, removed) do
    SocialServiceWeb.Endpoint.config_change(changed, removed)
    :ok
  end
end
