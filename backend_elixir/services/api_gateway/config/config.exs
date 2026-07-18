import Config

# API Gateway Configuration
config :api_gateway,
  auth_service_url: System.get_env("AUTH_SERVICE_URL") || "http://localhost:4001",
  user_service_url: System.get_env("USER_SERVICE_URL") || "http://localhost:4002",
  email_service_url: System.get_env("EMAIL_SERVICE_URL") || "http://localhost:4005",
  signaling_service_url: System.get_env("SIGNALING_SERVICE_URL") || "http://localhost:4003",
  matchmaking_service_url: System.get_env("MATCHMAKING_SERVICE_URL") || "http://localhost:4004",
  social_service_url: System.get_env("SOCIAL_SERVICE_URL") || "http://localhost:4006",
  # URL the matchmaking service uses to callback into this gateway for async notifications
  self_url: System.get_env("API_GATEWAY_SELF_URL") || "http://localhost:4000",
  # HMAC secret shared between gateway and internal services for X-Internal-Secret.
  # In :prod runtime.exs raises when unset — this default only applies to dev/test.
  internal_secret:
    System.get_env("INTERNAL_SECRET") || "dev_internal_secret_replace_in_production"

# Import environment specific config. This must remain at the bottom
# of this file so it overrides the configuration defined above.
import_config "#{config_env()}.exs"
