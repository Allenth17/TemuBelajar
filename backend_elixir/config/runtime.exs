import Config

# Database URL dari environment variable (override dev.exs)
if database_url = System.get_env("DATABASE_URL") do
  config :temubelajar, TemuBelajar.Repo,
    url: database_url,
    pool_size: String.to_integer(System.get_env("POOL_SIZE") || "50"),
    timeout: 5000,
    queue_target: 50,
    queue_interval: 1000,
    ownership_timeout: 5000
end

# Secret key dari env (wajib di production)
if secret_key_base = System.get_env("SECRET_KEY_BASE") do
  config :temubelajar, TemuBelajarWeb.Endpoint,
    secret_key_base: secret_key_base
end

# Untuk dev: baca dari .env di root project
if File.exists?(Path.join([__DIR__, "..", "..", ".env"])) do
  for line <- File.read!(Path.join([__DIR__, "..", "..", ".env"])) |> String.split("\n"),
      String.contains?(line, "=") do
    [key, value] = String.split(line, "=", parts: 2)
    System.put_env(String.trim(key), String.trim(value))
  end
end

# SMTP config dari .env
smtp_email = System.get_env("SMTP_EMAIL")
smtp_pass = System.get_env("SMTP_PASS")

if config_env() != :test && smtp_email && smtp_pass do
  config :temubelajar, TemuBelajar.Mailer,
    adapter: Swoosh.Adapters.SMTP,
    relay: "smtp.gmail.com",
    port: 587,
    tls: :always,
    username: smtp_email,
    password: smtp_pass,
    auth: :always,
    retries: 0,
    timeout: 1000,
    pool_size: 10
end

# Port dari env
port =
  System.get_env("PORT") || "4000"
  |> String.to_integer()

config :temubelajar, TemuBelajarWeb.Endpoint,
  http: [
    ip: {0, 0, 0, 0}, 
    port: port,
    thousand_island_options: [
      num_acceptors: 100
    ]
  ],
  secret_key_base:
    System.get_env("SECRET_KEY_BASE") ||
      "dev_secret_key_base_at_least_64_chars_long_replace_in_production_1234",
  render_errors: [view: TemuBelajarWeb.ErrorView, accepts: ~w(json), layout: false],
  pubsub_server: TemuBelajar.PubSub
