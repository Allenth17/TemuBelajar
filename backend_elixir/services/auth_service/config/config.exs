import Config

config :auth_service,
  ecto_repos: [AuthService.Repo]

# Configures Elixir's Logger
config :logger, :console,
  format: "$time $metadata[$level] $message\n",
  metadata: [:request_id]

# Use Jason for JSON parsing in Phoenix
config :phoenix, :json_library, Jason

# Swoosh API Client
config :swoosh,
  api_client: Swoosh.ApiClient.Finch,
  finch_name: AuthService.Finch

# Import environment config. This must remain at bottom
# of this file so it overrides the configuration defined above.
import_config "#{config_env()}.exs"
