import Config

config :temubelajar, TemuBelajar.Repo,
  username: "postgres",
  password: "Allenth17",
  hostname: "localhost",
  database: "temubelajar_test#{System.get_env("MIX_TEST_PARTITION")}",
  pool: Ecto.Adapters.SQL.Sandbox,
  pool_size: System.schedulers_online() * 2

config :temubelajar, TemuBelajarWeb.Endpoint,
  http: [ip: {127, 0, 0, 1}, port: 4002],
  secret_key_base: "test_secret_key_base_at_least_64_chars_long_replace_in_production_1234",
  server: false

config :logger, level: :warning
config :phoenix, :plug_init_mode, :runtime

config :temubelajar, TemuBelajar.Mailer, adapter: Swoosh.Adapters.Test
config :swoosh, :api_client, false
