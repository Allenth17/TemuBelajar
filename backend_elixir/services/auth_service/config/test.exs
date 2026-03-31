import Config

# Configure your database
config :auth_service, AuthService.Repo,
  username: "postgres",
  password: "Allenth17",
  hostname: "localhost",
  database: "temubelajar_auth_test",
  pool: Ecto.Adapters.SQL.Sandbox,
  pool_size: System.schedulers_online() * 2

# We don't run a server during test. If one is required,
# you can enable the server option below.
config :auth_service, AuthServiceWeb.Endpoint,
  http: [ip: {127, 0, 0, 1}, port: 4002],
  secret_key_base: "test_secret_key_base_auth_service_at_least_64_chars_long_replace_in_production",
  server: false

# Print only warnings and errors during test
config :logger, level: :warning
config :phoenix, :plug_init_mode, :runtime

# Swoosh Mailer configuration for test
config :auth_service, AuthService.Mailer, adapter: Swoosh.Adapters.Test
config :swoosh, :api_client, false
