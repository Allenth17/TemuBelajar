import Config

# Phoenix endpoint configuration
config :email_service, EmailServiceWeb.Endpoint,
  http: [
    ip: {0, 0, 0, 0},
    port: String.to_integer(System.get_env("PORT") || "4005"),
    thousand_island_options: [
      num_acceptors: 10
    ]
  ],
  secret_key_base:
    System.get_env("SECRET_KEY_BASE") ||
    "dev_secret_key_base_email_service_at_least_64_chars_long_replace_in_production"

# SMTP config from environment
config :email_service, EmailService.Mailer,
  adapter: Swoosh.Adapters.SMTP,
  relay: "smtp.gmail.com",
  port: 587,
  tls: :always,
  username: System.get_env("SMTP_EMAIL"),
  password: System.get_env("SMTP_PASS"),
  auth: :always,
  retries: 0,
  timeout: 5000,
  pool_size: 5
