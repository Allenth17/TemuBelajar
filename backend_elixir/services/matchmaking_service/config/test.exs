import Config

# We don't run a server during test. If one is required,
# you can enable the server option below.
config :matchmaking_service, MatchmakingServiceWeb.Endpoint,
  http: [ip: {127, 0, 0, 1}, port: 4002],
  secret_key_base: "test_secret_key_base_matchmaking_service_at_least_64_chars_long_replace_in_production",
  server: false

# Print only warnings and errors during test
config :logger, level: :warning
config :phoenix, :plug_init_mode, :runtime
config :matchmaking_service, auth_service_url: "http://localhost:4001"
