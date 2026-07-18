import Config

config :matchmaking_service, MatchmakingServiceWeb.Endpoint,
  adapter: Bandit.PhoenixAdapter,
  http: [
    ip: {127, 0, 0, 1},
    port: 4004,
    thousand_island_options: [
      num_acceptors: 100
    ]
  ],
  check_origin: false,
  code_reloader: true,
  debug_errors: true,
  secret_key_base:
    "dev_secret_key_base_matchmaking_service_at_least_64_chars_long_replace_in_production",
  render_errors: [view: MatchmakingServiceWeb.ErrorView, accepts: ~w(json), layout: false],
  pubsub_server: MatchmakingService.PubSub

# Phase 8.25 — `auth_service_url` previously lived INSIDE the
# `MatchmakingServiceWeb.Endpoint` config block above (right after
# `pubsub_server`). The endpoint config is the Phoenix.Endpoint options
# tuple; placing `:auth_service_url` there is silently ignored by
# Bandit/Phoenix, and code that reads `Application.get_env(:matchmaking_service,
# :auth_service_url)` only worked because `config/config.exs:13` sets
# the same key at app level. The dev override never took effect. We now
# set it at the app level so a dev override (e.g. pointing at a remote
# auth_service) actually applies.
config :matchmaking_service,
  auth_service_url: System.get_env("AUTH_SERVICE_URL") || "http://localhost:4001"
