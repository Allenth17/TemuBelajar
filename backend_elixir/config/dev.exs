import Config

config :temubelajar, TemuBelajar.Repo,
  username: "postgres",
  password: "Allenth17",
  hostname: "localhost",
  database: "TemuBelajar",
  stacktrace: true,
  show_sensitive_data_on_connection_error: true,
  pool_size: 50,
  timeout: 5000,
  queue_target: 50,
  queue_interval: 1000,
  ownership_timeout: 5000

config :temubelajar, TemuBelajarWeb.Endpoint,
  http: [
    ip: {127, 0, 0, 1}, 
    port: 4000,
    thousand_island_options: [
      num_acceptors: 100
    ]
  ],
  check_origin: false,
  code_reloader: true,
  debug_errors: true,
  secret_key_base: "dev_secret_key_base_at_least_64_chars_long_replace_in_production_1234",
  watchers: [],
  render_errors: [view: TemuBelajarWeb.ErrorView, accepts: ~w(json), layout: false],
  pubsub_server: TemuBelajar.PubSub

config :logger, level: :debug
config :phoenix, :stacktrace_depth, 20
config :phoenix, :plug_init_mode, :runtime
