import Config

if config_env() == :prod do
  internal_secret =
    System.get_env("INTERNAL_SECRET") ||
      raise "INTERNAL_SECRET environment variable is missing — required for service-to-service auth"

  config :api_gateway, internal_secret: internal_secret

  # Phase 8.16 — prod CORS allowlist. Default is the canonical client origin
  # `https://temubelajar.id`. Override via CORS_ALLOWED_ORIGINS (comma list)
  # for multi-domain deployments, or set it to "" to reject all cross-origin
  # requests when the gateway is server-to-server only.
  cors_origins =
    case System.get_env("CORS_ALLOWED_ORIGINS") do
      nil -> ["https://temubelajar.id"]
      "" -> []
      raw -> raw |> String.split(",", trim: true) |> Enum.map(&String.trim/1)
    end

  config :api_gateway, cors_origins: cors_origins
end

# API Gateway — Runtime Configuration
# Service URLs can be overridden via environment variables for Docker / production.
config :api_gateway,
  auth_service_url: System.get_env("AUTH_SERVICE_URL") || "http://localhost:4001",
  user_service_url: System.get_env("USER_SERVICE_URL") || "http://localhost:4002",
  email_service_url: System.get_env("EMAIL_SERVICE_URL") || "http://localhost:4005",
  signaling_service_url: System.get_env("SIGNALING_SERVICE_URL") || "http://localhost:4003",
  matchmaking_service_url: System.get_env("MATCHMAKING_SERVICE_URL") || "http://localhost:4004",
  social_service_url: System.get_env("SOCIAL_SERVICE_URL") || "http://localhost:4006",
  self_url: System.get_env("API_GATEWAY_SELF_URL") || "http://localhost:4000"

# ── API Gateway runtime endpoint config ────────────────────────────────────
# Phase 8.14 — when MIX_ENV=prod / a release, we additionally append a gated
# HTTPS listener so deployments can flip TLS on with env vars. Safe default =
# HTTP only. Bandit supports `transport: :ssl` + `ssl_options:` (mirrors
# Erlang :ssl.listen/2). See Bandit docs.
bandit_https_opts =
  if config_env() == :prod and System.get_env("ENABLE_HTTPS") == "true" do
    cert_path = System.get_env("TLS_CERT_PATH")
    key_path = System.get_env("TLS_KEY_PATH")

    if is_nil(cert_path) or is_nil(key_path) do
      raise "ENABLE_HTTPS=true requires TLS_CERT_PATH and TLS_KEY_PATH env vars (PEM files on disk)."
    end

    [
      ip: {0, 0, 0, 0},
      port: String.to_integer(System.get_env("HTTPS_PORT") || "443"),
      thousand_island_options: [num_acceptors: 20],
      transport: :ssl,
      cipher_suite: :strong,
      ssl_options: [
        certfile: cert_path,
        keyfile: key_path
      ]
    ]
  else
    nil
  end

bandit_http_opts =
  if config_env() == :prod and System.get_env("ENABLE_HTTPS") == "true" and
       System.get_env("DISABLE_HTTP_AFTER_TLS") == "true" do
    nil
  else
    [
      ip: {0, 0, 0, 0},
      port: String.to_integer(System.get_env("PORT") || "4000"),
      # Gateway sits behind a load balancer — 20 acceptors is ample
      thousand_island_options: [
        num_acceptors: 20
      ]
    ]
  end

config :api_gateway,
       ApiGatewayWeb.Endpoint,
       (
         base_opts = [
           secret_key_base:
             System.get_env("SECRET_KEY_BASE") ||
               "dev_secret_key_base_api_gateway_at_least_64_chars_long_replace_in_production",
           render_errors: [view: ApiGatewayWeb.ErrorJSON, accepts: ~w(json), layout: false],
           pubsub_server: ApiGateway.PubSub
         ]

         base_opts
         |> then(fn opts ->
           if bandit_http_opts, do: Keyword.put(opts, :http, bandit_http_opts), else: opts
         end)
         |> then(fn opts ->
           if bandit_https_opts, do: Keyword.put(opts, :https, bandit_https_opts), else: opts
         end)
       )
