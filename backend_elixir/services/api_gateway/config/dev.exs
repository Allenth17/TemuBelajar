import Config

# Phase 8.16 — dev CORS allowlist defaults to "*" so local emulators and
# the npm run dev forge work without extra setup. Override by setting
# CORS_ALLOWED_ORIGINS=https://localhost:3000,... in .env before start_all.
allowed =
  case System.get_env("CORS_ALLOWED_ORIGINS") do
    nil -> ["*"]
    "" -> []
    raw -> raw |> String.split(",", trim: true) |> Enum.map(&String.trim/1)
  end

config :api_gateway, cors_origins: allowed

config :api_gateway, ApiGatewayWeb.Endpoint,
  adapter: Bandit.PhoenixAdapter,
  http: [
    # Bind to all interfaces so the Android/iOS devices on LAN can connect
    ip: {0, 0, 0, 0},
    port: 4000,
    thousand_island_options: [
      num_acceptors: 20
    ]
  ],
  check_origin: false,
  code_reloader: true,
  debug_errors: true,
  secret_key_base: "dev_secret_key_base_api_gateway_at_least_64_chars_long_replace_in_production",
  render_errors: [view: ApiGatewayWeb.ErrorJSON, accepts: ~w(json), layout: false],
  pubsub_server: ApiGateway.PubSub
