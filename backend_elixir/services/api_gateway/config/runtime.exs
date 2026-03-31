import Config

# API Gateway — Runtime Configuration
# Service URLs can be overridden via environment variables for Docker / production.
config :api_gateway,
  auth_service_url: System.get_env("AUTH_SERVICE_URL") || "http://localhost:4001",
  user_service_url: System.get_env("USER_SERVICE_URL") || "http://localhost:4002",
  email_service_url: System.get_env("EMAIL_SERVICE_URL") || "http://localhost:4005",
  signaling_service_url: System.get_env("SIGNALING_SERVICE_URL") || "http://localhost:4003",
  matchmaking_service_url: System.get_env("MATCHMAKING_SERVICE_URL") || "http://localhost:4004"

config :api_gateway, ApiGatewayWeb.Endpoint,
  http: [
    ip: {0, 0, 0, 0},
    port: String.to_integer(System.get_env("PORT") || "4000"),
    # Gateway sits behind a load balancer — 20 acceptors is ample
    thousand_island_options: [
      num_acceptors: 20
    ]
  ],
  secret_key_base:
    System.get_env("SECRET_KEY_BASE") ||
      "dev_secret_key_base_api_gateway_at_least_64_chars_long_replace_in_production",
  render_errors: [view: ApiGatewayWeb.ErrorJSON, accepts: ~w(json), layout: false],
  pubsub_server: ApiGateway.PubSub
