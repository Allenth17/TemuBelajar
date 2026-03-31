import Config

# We don't run a server during test. If one is required,
# you can enable the server option below.
config :email_service, EmailServiceWeb.Endpoint,
  http: [ip: {127, 0, 0, 1}, port: 4006],
  secret_key_base: "test_secret_key_base_email_service_at_least_64_chars_long_replace_in_production",
  server: false

# Use Swoosh test adapter — captured emails are stored in the process mailbox
# and inspectable via Swoosh.TestAssertions helpers.
config :email_service, EmailService.Mailer,
  adapter: Swoosh.Adapters.Test

# Disable the Swoosh API client in tests (not needed for the Test adapter)
config :swoosh, :api_client, false
# Print only warnings and errors during test
config :logger, level: :warning

config :phoenix, :plug_init_mode, :runtime
