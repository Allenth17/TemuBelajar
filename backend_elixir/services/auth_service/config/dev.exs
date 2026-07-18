import Config

# Phase 0.11 — removed hardcoded DB password default. Local dev must set
# POSTGRES_PASSWORD (e.g. in `.env` loaded by start_all.sh). No fallback
# so accidental committment of a real password is impossible.
config :auth_service, AuthService.Repo,
  username: System.get_env("POSTGRES_USER") || "postgres",
  password: System.get_env("POSTGRES_PASSWORD") || raise("POSTGRES_PASSWORD is required for dev"),
  hostname: System.get_env("POSTGRES_HOST") || "localhost",
  database: System.get_env("POSTGRES_DB") || "temubelajar_auth",
  stacktrace: true,
  show_sensitive_data_on_connection_error: false,
  pool_size: 10,
  timeout: 5000,
  queue_target: 50,
  queue_interval: 1000,
  ownership_timeout: 5000

config :auth_service, AuthServiceWeb.Endpoint,
  adapter: Bandit.PhoenixAdapter,
  http: [
    ip: {127, 0, 0, 1},
    port: 4001,
    thousand_island_options: [
      num_acceptors: 100
    ]
  ],
  check_origin: false,
  code_reloader: true,
  debug_errors: true,
  secret_key_base:
    "dev_secret_key_base_auth_service_at_least_64_chars_long_replace_in_production",
  render_errors: [view: AuthServiceWeb.ErrorView, accepts: ~w(json), layout: false],
  pubsub_server: AuthService.PubSub

# Swoosh Mailer configuration — Phase 0.11 removed the hardcoded SMTP password
# default. Local dev must export SMTP_PASS (or use the test adapter if no SMTP).
config :auth_service, AuthService.Mailer,
  adapter: Swoosh.Adapters.SMTP,
  relay: "smtp.gmail.com",
  port: 587,
  username: System.get_env("SMTP_EMAIL") || "temubelajar.app@gmail.com",
  password: System.get_env("SMTP_PASS") || raise("SMTP_PASS is required for dev"),
  tls: :always,
  auth: :always,
  ssl: false
