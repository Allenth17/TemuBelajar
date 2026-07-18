import Config

# Phase 8.16 — dev CORS allowlist defaults to "*" so local Postman / curl
# keep working without env overrides. Override with CORS_ALLOWED_ORIGINS.
allowed =
  case System.get_env("CORS_ALLOWED_ORIGINS") do
    nil -> ["*"]
    "" -> []
    raw -> raw |> String.split(",", trim: true) |> Enum.map(&String.trim/1)
  end

config :social_service, cors_origins: allowed

config :social_service, SocialService.Repo,
  username: System.get_env("POSTGRES_USER") || "postgres",
  password: System.get_env("POSTGRES_PASSWORD") || "Allenth17",
  hostname: System.get_env("POSTGRES_HOST") || "localhost",
  database: System.get_env("POSTGRES_DB") || "temubelajar_social",
  # Phase 8.17 — never surface the DB URL on a connection error in dev
  # (start_all.sh runs MIX_ENV=dev as a "production-like" local run).
  show_sensitive_data_on_connection_error: false,
  pool_size: 10,
  queue_target: 5_000,
  queue_interval: 1_000,
  timeout: 30_000

config :social_service, SocialServiceWeb.Endpoint,
  adapter: Bandit.PhoenixAdapter,
  http: [ip: {127, 0, 0, 1}, port: 4006],
  secret_key_base: "dev_secret_key_base_social_service_at_least_64_chars_long_replace_in_prod",
  server: true

config :logger, :console,
  format: "$time $metadata[$level] $message\n",
  metadata: [:request_id]

config :phoenix, :json_library, Jason
