defmodule ApiGatewayWeb.Router do
  use ApiGatewayWeb, :router

  pipeline :api do
    plug(:accepts, ["json"])
    # Phase 3.20 — per-IP / per-route in-memory rate limit on the
    # auth-issuing endpoints (login / verify-otp / register). 30 req/min
    # each. Internal-only paths and health are not rate-limited by this
    # plug (the configured `routes` allowlist is matched against the
    # request's matched path). See `ApiGatewayWeb.Plugs.RateLimit` for
    # the full semantics and the ETS-backed implementation.
    plug(ApiGatewayWeb.Plugs.RateLimit)
  end

  scope "/api", ApiGatewayWeb do
    pipe_through(:api)
    # Health check
    get("/health", GatewayController, :health)

    # ── Internal callbacks from microservices (no auth required — internal only) ─
    # Matchmaking service posts here to notify waiting clients of match events
    post("/internal/notify/:email", NotifyController, :notify)

    # ── Auth routes → Auth Service (port 4001) ──────────────────────────────
    post("/register", GatewayController, :register)
    post("/verify-otp", GatewayController, :verify_otp)
    post("/resend-otp", GatewayController, :resend_otp)
    post("/login", GatewayController, :login)
    post("/logout", GatewayController, :logout)
    get("/me", GatewayController, :me)
    delete("/expired-sessions", GatewayController, :cleanup_sessions)

    # ── User routes → User Service (port 4002) ──────────────────────────────
    get("/users", GatewayController, :list_users)
    get("/users/search", GatewayController, :search_users)
    get("/user/:email", GatewayController, :get_user)
    put("/user/:email", GatewayController, :update_user)

    # ── Signaling routes → Signaling Service (port 4003) ────────────────────
    # NOTE: signaling is WebSocket-only via SignalingProxyChannel. HTTP
    # proxies previously returned 404 from signaling_service — removed
    # rather than maintain dead routes (see 7.5).

    # ── Matchmaking routes → Matchmaking Service (port 4004) ────────────────
    post("/matchmaking/join", GatewayController, :matchmaking_join)
    post("/matchmaking/leave", GatewayController, :matchmaking_leave)

    # ── Social routes → Social Service (port 4006) ───────────────────────────
    post("/social/follow", GatewayController, :social_follow)
    delete("/social/follow/:target", GatewayController, :social_unfollow)
    get("/social/followers/:email", GatewayController, :social_followers)
    get("/social/following/:email", GatewayController, :social_following)
    get("/social/profile/:email", GatewayController, :social_profile)

    post("/social/friend-request", GatewayController, :social_send_friend_request)
    put("/social/friend-request/:from", GatewayController, :social_respond_friend_request)
    delete("/social/friend/:target", GatewayController, :social_unfriend)
    get("/social/friends/:email", GatewayController, :social_friends)
    get("/social/friend-requests/pending", GatewayController, :social_pending_requests)

    post("/social/block", GatewayController, :social_block)
    delete("/social/block/:target", GatewayController, :social_unblock)

    post("/social/report", GatewayController, :social_report)
  end
end
