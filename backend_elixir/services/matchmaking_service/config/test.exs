import Config

# We don't run a server during test. If one is required,
# you can enable the server option below.
config :matchmaking_service, MatchmakingServiceWeb.Endpoint,
  http: [ip: {127, 0, 0, 1}, port: 4002],
  secret_key_base:
    "test_secret_key_base_matchmaking_service_at_least_64_chars_long_replace_in_production",
  server: false

# Print only warnings and errors during test
config :logger, level: :warning
config :phoenix, :plug_init_mode, :runtime
config :matchmaking_service, auth_service_url: "http://localhost:4001"

# Phase 5.31 — disable the block-list social_service HTTP lookup in unit
# tests by leaving social_service_url unset. fetch_blocked_set/1 short-
# circuits to an empty MapSet when the URL is nil, so tests that don't
# wire a mock social_service still match peers freely. Tests that want to
# exercise the real lookup path can override via
# Application.put_env(:matchmaking_service, :social_service_url, ...).
config :matchmaking_service, social_service_url: nil
