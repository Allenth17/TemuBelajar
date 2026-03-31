import Config

# We don't run a server during test. If one is required,
# you can enable the server option below.
config :user_service, UserServiceWeb.Endpoint,
  http: [ip: {127, 0, 0, 1}, port: 4002],
  secret_key_base: "test_secret_key_base_user_service_at_least_64_chars_long_replace_in_production",
  server: false

# Print only warnings and errors during test
config :logger, level: :warning

# Configure your database
config :user_service, UserService.Repo,
  username: "postgres",
  password: "Allenth17",
  hostname: "localhost",
  database: "temubelajar_user_test",
  pool: Ecto.Adapters.SQL.Sandbox,
  pool_size: 10
