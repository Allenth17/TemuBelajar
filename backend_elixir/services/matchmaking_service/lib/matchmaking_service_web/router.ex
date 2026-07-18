defmodule MatchmakingServiceWeb.Router do
  use MatchmakingServiceWeb, :router

  pipeline :api do
    plug(:accepts, ["json"])
  end

  # Internal-only routes (verify-pair, end-pair) — only services with the
  # shared HMAC secret may call these. join/leave are still proxied via the
  # gateway and the gateway overwrites email + notify_url — see 0.3.
  pipeline :internal do
    plug(MatchmakingServiceWeb.Plugs.InternalAuth)
  end

  scope "/api", MatchmakingServiceWeb do
    pipe_through(:api)

    get("/health", HealthController, :health)

    post("/matchmaking/join", MatchmakingController, :join)
    post("/matchmaking/leave", MatchmakingController, :leave)
  end

  scope "/api", MatchmakingServiceWeb do
    pipe_through([:api, :internal])

    post("/matchmaking/end-pair", MatchmakingController, :end_pair)

    # Phase 0.7 — signaling_service / gateway uses this to verify a client
    # joining a signaling channel actually owns the pair_id (call-hijack
    # prevention). Authenticated by X-Internal-Secret so external callers
    # can't probe arbitrary pair_ids.
    post("/matchmaking/verify-pair", MatchmakingController, :verify_pair)
  end
end
