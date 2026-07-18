import Config

# Phase 8.16 — test CORS allowlist: "*" by default for ConnTest simplicity,
# overridable via CORS_ALLOWED_ORIGINS if a test needs stricter behaviour.
allowed =
  case System.get_env("CORS_ALLOWED_ORIGINS") do
    nil -> ["*"]
    "" -> []
    raw -> raw |> String.split(",", trim: true) |> Enum.map(&String.trim/1)
  end

config :api_gateway, cors_origins: allowed

# Configure your database
config :api_gateway, ApiGateway.Repo,
  username: "postgres",
  password: "Allenth17",
  hostname: "localhost",
  database: "temubelajar_api_gateway_test",
  pool: Ecto.Adapters.SQL.Sandbox,
  pool_size: System.schedulers_online() * 2

# We don't run a server during test. If one is required,
# you can enable the server option below.
config :api_gateway, ApiGatewayWeb.Endpoint,
  http: [ip: {127, 0, 0, 1}, port: 4002],
  secret_key_base:
    "test_secret_key_base_api_gateway_at_least_64_chars_long_replace_in_production",
  render_errors: [view: ApiGatewayWeb.ErrorJSON, accepts: ~w(json), layout: false],
  server: false

# Print only warnings and errors during test
config :logger, level: :warning
config :phoenix, :plug_init_mode, :runtime
