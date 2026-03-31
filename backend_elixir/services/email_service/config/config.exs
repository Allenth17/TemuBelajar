import Config

# Configures Elixir's Logger
config :logger, :console,
  format: "$time $metadata[$level] $message\n",
  metadata: [:request_id]

# Use Jason for JSON parsing in Phoenix
config :phoenix, :json_library, Jason

# SMTP config
config :email_service, EmailService.Mailer,
  adapter: Swoosh.Adapters.SMTP,
  relay: "smtp.gmail.com",
  port: 587,
  tls: :always,
  username: System.get_env("SMTP_EMAIL"),
  password: System.get_env("SMTP_PASS"),
  auth: :always,
  retries: 0,
  timeout: 1000,
  pool_size: 10

# Import environment config. This must remain at the bottom
# of this file so it overrides the configuration defined above.
import_config "#{config_env()}.exs"
