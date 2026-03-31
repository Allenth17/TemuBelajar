defmodule SocialServiceWeb.Router do
  use SocialServiceWeb, :router

  pipeline :api do
    plug :accepts, ["json"]
  end

  scope "/api", SocialServiceWeb do
    pipe_through :api

    get "/health", HealthController, :index

    # ── Follow / Unfollow ────────────────────────────────────────────────────
    post   "/social/follow",                  SocialController, :follow
    delete "/social/follow/:target",          SocialController, :unfollow
    get    "/social/followers/:email",        SocialController, :followers
    get    "/social/following/:email",        SocialController, :following
    get    "/social/profile/:email",          SocialController, :profile_social

    # ── Friend Requests ──────────────────────────────────────────────────────
    post   "/social/friend-request",          SocialController, :send_friend_request
    put    "/social/friend-request/:from",    SocialController, :respond_friend_request
    delete "/social/friend/:target",          SocialController, :unfriend
    get    "/social/friends/:email",          SocialController, :friends
    get    "/social/friend-requests/pending", SocialController, :pending_requests

    # ── Block ────────────────────────────────────────────────────────────────
    post   "/social/block",                   SocialController, :block
    delete "/social/block/:target",           SocialController, :unblock

    # ── Report ───────────────────────────────────────────────────────────────
    post   "/social/report",                  SocialController, :report

    # ── Internal (matchmaking guard) ─────────────────────────────────────────
    get    "/internal/should-exclude",        SocialController, :should_exclude
  end
end
