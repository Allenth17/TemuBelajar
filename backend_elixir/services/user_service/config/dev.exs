import Config

# For development, we disable any cache and enable
# debugging and code reloading.
config :user_service, UserService.Repo,
  username: System.get_env("POSTGRES_USER") || "postgres",
  password: System.get_env("POSTGRES_PASSWORD") || "Allenth17",
  hostname: System.get_env("POSTGRES_HOST") || "localhost",
  database: System.get_env("POSTGRES_DB") || "temubelajar_user",
  stacktrace: true,
  show_sensitive_data_on_connection_error: true,
  pool_size: 10,
  timeout: 5000,
  queue_target: 50,
  queue_interval: 1000,
  ownership_timeout: 5000

config :user_service, UserServiceWeb.Endpoint, adapter: Bandit.PhoenixAdapter,
  http: [
    ip: {127, 0, 0, 1},
    port: 4002,
    thousand_island_options: [
      num_acceptors: 100
    ]
  ],
  check_origin: false,
  code_reloader: true,
  debug_errors: true,
  secret_key_base: "dev_secret_key_base_user_service_at_least_64_chars_long_replace_in_production",
  render_errors: [view: UserServiceWeb.ErrorView, accepts: ~w(json), layout: false],
  pubsub_server: UserService.PubSub
