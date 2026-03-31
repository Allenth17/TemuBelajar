import Config

# Read env vars at runtime (Docker / release env)
if config_env() == :prod do
  database_url = System.get_env("DATABASE_URL") ||
    raise "DATABASE_URL environment variable is missing."

  config :social_service, SocialService.Repo,
    url: database_url
end

pool_size = System.get_env("POOL_SIZE", "10") |> String.to_integer()
port = System.get_env("PORT", "4006") |> String.to_integer()

secret_key_base =
  System.get_env("SECRET_KEY_BASE") ||
    "dev_secret_key_base_social_service_at_least_64_chars_long_replace_in_production"

config :social_service, SocialService.Repo,
  pool_size: pool_size,
  # Tune for high concurrency — short queue timeout
  queue_target: 2_000,
  queue_interval: 1_000,
  timeout: 15_000,
  # Reduce prepared statement cache to save memory
  prepare: :unnamed

config :social_service, SocialServiceWeb.Endpoint,
  http: [
    ip: {0, 0, 0, 0},
    port: port,
    # num_acceptors at 1.2KB each = ~12KB idle cost
    thousand_island_options: [num_acceptors: 10]
  ],
  secret_key_base: secret_key_base,
  server: true

config :logger, level: :info
