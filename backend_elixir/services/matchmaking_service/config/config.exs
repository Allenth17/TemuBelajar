import Config

# Configures Elixir's Logger
config :logger, :console,
  format: "$time $metadata[$level] $message\n",
  metadata: [:request_id]

# Use Jason for JSON parsing in Phoenix
config :phoenix, :json_library, Jason

# Service URLs (overridden per environment and via runtime.exs / .env)
config :matchmaking_service,
  auth_service_url: System.get_env("AUTH_SERVICE_URL") || "http://localhost:4001",
  api_gateway_url: System.get_env("API_GATEWAY_URL") || "http://localhost:4000"

# Import environment specific config. This must remain at the bottom
# of this file so it overrides the configuration defined above.
import_config "#{config_env()}.exs"
