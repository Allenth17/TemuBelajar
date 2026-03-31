import Config

config :social_service, SocialService.Repo,
  username: System.get_env("POSTGRES_USER") || "postgres",
  password: System.get_env("POSTGRES_PASSWORD") || "Allenth17",
  hostname: System.get_env("POSTGRES_HOST") || "localhost",
  database: System.get_env("POSTGRES_DB") || "temubelajar_social",
  pool_size: 10,
  queue_target: 5_000,
  queue_interval: 1_000,
  timeout: 30_000

config :social_service, SocialServiceWeb.Endpoint, adapter: Bandit.PhoenixAdapter,
  http: [ip: {127, 0, 0, 1}, port: 4006],
  secret_key_base: "dev_secret_key_base_social_service_at_least_64_chars_long_replace_in_prod",
  server: true

config :logger, :console,
  format: "$time $metadata[$level] $message\n",
  metadata: [:request_id]

config :phoenix, :json_library, Jason
