defmodule SocialServiceWeb.Router do
  use SocialServiceWeb, :router

  pipeline :api do
    plug(:accepts, ["json"])
  end

  # Phase 0.1 / 0.17 / 7.18 — every social endpoint (including the list /
  # profile reads that previously trusted the plug implicitly) requires the
  # gateway-injected identity chain: `X-Internal-Secret` (HMAC shared with
  # the gateway) + `X-Caller-Email` (server-derived from the Bearer, never
  # trusted from a browser client). See `SocialServiceWeb.Plugs.InternalAuth`.
  # Health stays open; the matchmaking guard endpoints also carry the secret.
  pipeline :internal do
    plug(SocialServiceWeb.Plugs.InternalAuth)
  end

  scope "/api", SocialServiceWeb do
    pipe_through(:api)

    get("/health", HealthController, :index)
  end

  scope "/api", SocialServiceWeb do
    pipe_through([:api, :internal])

    # ── Follow / Unfollow ────────────────────────────────────────────────────
    post("/social/follow", SocialController, :follow)
    delete("/social/follow/:target", SocialController, :unfollow)
    get("/social/followers/:email", SocialController, :followers)
    get("/social/following/:email", SocialController, :following)
    get("/social/profile/:email", SocialController, :profile_social)

    # ── Friend Requests ──────────────────────────────────────────────────────
    post("/social/friend-request", SocialController, :send_friend_request)
    put("/social/friend-request/:from", SocialController, :respond_friend_request)
    delete("/social/friend/:target", SocialController, :unfriend)
    get("/social/friends/:email", SocialController, :friends)
    get("/social/friend-requests/pending", SocialController, :pending_requests)

    # ── Block ────────────────────────────────────────────────────────────────
    post("/social/block", SocialController, :block)
    delete("/social/block/:target", SocialController, :unblock)

    # ── Report ───────────────────────────────────────────────────────────────
    post("/social/report", SocialController, :report)

    # ── Internal (matchmaking guard) ─────────────────────────────────────────
    get("/internal/should-exclude", SocialController, :should_exclude)
    # Phase 5.31 — block-list fetch for matchmaking_service
    get("/internal/blocked-by/:email", SocialController, :blocked_by)
  end
end
