import Config

# Phase 0.9 / 0.11 — production secrets MUST come from env. We never
# silently fall back to the publicly-known dev value in :prod.
if config_env() == :prod do
  System.get_env("SECRET_KEY_BASE") ||
    raise "SECRET_KEY_BASE environment variable is missing — required in production"

  internal_secret =
    System.get_env("INTERNAL_SECRET") ||
      raise "INTERNAL_SECRET environment variable is missing — required for service-to-service auth"

  config :auth_service, internal_secret: internal_secret
end

# Database URL dari environment variable (override dev.exs)
if database_url = System.get_env("DATABASE_URL") do
  config :auth_service, AuthService.Repo,
    url: database_url,
    pool_size: String.to_integer(System.get_env("POOL_SIZE") || "10"),
    timeout: 5000,
    queue_target: 100,
    queue_interval: 1000,
    ownership_timeout: 5000
end

config :auth_service,
  internal_secret:
    System.get_env("INTERNAL_SECRET") || "dev_internal_secret_replace_in_production"

# Phoenix endpoint configuration
config :auth_service, AuthServiceWeb.Endpoint,
  http: [
    ip: {0, 0, 0, 0},
    port: String.to_integer(System.get_env("PORT") || "4001"),
    thousand_island_options: [
      num_acceptors: 20
    ]
  ],
  secret_key_base:
    System.get_env("SECRET_KEY_BASE") ||
      "dev_secret_key_base_auth_service_at_least_64_chars_long_replace_in_production",
  render_errors: [view: AuthServiceWeb.ErrorView, accepts: ~w(json), layout: false],
  pubsub_server: AuthService.PubSub
