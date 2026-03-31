import Config

config :social_service, SocialService.Repo,
  url: "postgres://postgres:postgres@localhost/temubelajar_social_test",
  pool: Ecto.Adapters.SQL.Sandbox,
  pool_size: 5

config :social_service, SocialServiceWeb.Endpoint,
  http: [ip: {127, 0, 0, 1}, port: 4066],
  secret_key_base: "test_secret_key_base_social_service_at_least_64_chars_long_xxxxxxx_",
  server: false

config :logger, level: :warning
