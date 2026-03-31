import Config

# Phoenix endpoint configuration
config :signaling_service, SignalingServiceWeb.Endpoint,
  http: [
    ip: {0, 0, 0, 0},
    port: String.to_integer(System.get_env("PORT") || "4003"),
    thousand_island_options: [
      num_acceptors: 20
    ]
  ],
  secret_key_base:
    System.get_env("SECRET_KEY_BASE") ||
      "dev_secret_key_base_signaling_service_at_least_64_chars_long_replace_in_production",
  render_errors: [view: SignalingServiceWeb.ErrorView, accepts: ~w(json), layout: false],
  pubsub_server: SignalingService.PubSub


config :signaling_service,
  auth_service_url:
    System.get_env("AUTH_SERVICE_URL") || "http://localhost:4001",
  matchmaking_service_url:
    System.get_env("MATCHMAKING_SERVICE_URL") || "http://localhost:4004"
