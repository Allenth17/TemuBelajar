import Config

config :email_service, EmailServiceWeb.Endpoint, adapter: Bandit.PhoenixAdapter,
  http: [
    ip: {127, 0, 0, 1},
    port: 4005,
    thousand_island_options: [
      num_acceptors: 50
    ]
  ],
  check_origin: false,
  code_reloader: true,
  debug_errors: true,
  secret_key_base: "dev_secret_key_base_email_service_at_least_64_chars_long_replace_in_production",
  render_errors: [view: EmailServiceWeb.ErrorView, accepts: ~w(json), layout: false],
  pubsub_server: EmailService.PubSub

# SMTP config for development (uses test adapter unless overridden)
config :email_service, EmailService.Mailer,
  adapter: Swoosh.Adapters.SMTP,
  relay: "smtp.gmail.com",
  port: 587,
  tls: :always,
  username: System.get_env("SMTP_EMAIL"),
  password: System.get_env("SMTP_PASS"),
  auth: :always,
  retries: 0,
  timeout: 5_000,
  pool_size: 5
