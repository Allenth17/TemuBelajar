import Config

# Phase 8.16 — test CORS allowlist defaults to "*"; override via env.
allowed =
  case System.get_env("CORS_ALLOWED_ORIGINS") do
    nil -> ["*"]
    "" -> []
    raw -> raw |> String.split(",", trim: true) |> Enum.map(&String.trim/1)
  end

config :social_service, cors_origins: allowed

config :social_service, SocialService.Repo,
  url: "postgres://postgres:postgres@localhost/temubelajar_social_test",
  pool: Ecto.Adapters.SQL.Sandbox,
  pool_size: 5

config :social_service, SocialServiceWeb.Endpoint,
  http: [ip: {127, 0, 0, 1}, port: 4066],
  secret_key_base: "test_secret_key_base_social_service_at_least_64_chars_long_xxxxxxx_",
  server: false

config :logger, level: :warning
