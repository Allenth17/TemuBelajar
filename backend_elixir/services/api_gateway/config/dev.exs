import Config

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
