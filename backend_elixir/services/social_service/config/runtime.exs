import Config

# Read env vars at runtime (Docker / release env)
if config_env() == :prod do
  System.get_env("DATABASE_URL") ||
    raise "DATABASE_URL environment variable is missing."

  System.get_env("SECRET_KEY_BASE") ||
    raise "SECRET_KEY_BASE environment variable is missing — required in production"

  internal_secret =
    System.get_env("INTERNAL_SECRET") ||
      raise "INTERNAL_SECRET environment variable is missing — required for service-to-service auth"

  config :social_service, internal_secret: internal_secret

  config :social_service, SocialService.Repo, url: System.get_env("DATABASE_URL")

  # Phase 8.16 — prod CORS allowlist. The social service is reached via the
  # gateway (X-Caller-Email injected); browser clients don't talk to it
  # directly in prod. Default is the canonical client origin. Override via
  # CORS_ALLOWED_ORIGINS (comma list); "" rejects all cross-origin calls.
  cors_origins =
    case System.get_env("CORS_ALLOWED_ORIGINS") do
      nil -> ["https://temubelajar.id"]
      "" -> []
      raw -> raw |> String.split(",", trim: true) |> Enum.map(&String.trim/1)
    end

  config :social_service, cors_origins: cors_origins
end

pool_size = System.get_env("POOL_SIZE", "10") |> String.to_integer()
port = System.get_env("PORT", "4006") |> String.to_integer()

secret_key_base =
  System.get_env("SECRET_KEY_BASE") ||
    "dev_secret_key_base_social_service_at_least_64_chars_long_replace_in_production"

# Phase 8.21 — Phoenix requires `secret_key_base` to be at least 64 bytes
# (it's used to encrypt/96-byte tokens + cookies). The previous dev fallback
# strings ("..._in_prod" / "..._in_production") differed across services and
# the shorter one sat barely over the limit (73 bytes), so a future trim
# would silently degrade session security. Assert here, at startup, for
# every environment (dev included — `start_all.sh` runs MIX_ENV=dev as a
# production-like local run so dev configs are real configs).
unless byte_size(secret_key_base) >= 64 do
  raise """
  social_service: secret_key_base must be at least 64 bytes long
  (got #{byte_size(secret_key_base)} bytes). Set SECRET_KEY_BASE to a
  64+-byte random string (e.g. `mix phx.gen.secret 64`).
  """
end

config :social_service,
  internal_secret:
    System.get_env("INTERNAL_SECRET") || "dev_internal_secret_replace_in_production"

config :social_service, SocialService.Repo,
  pool_size: pool_size,
  # Tune for high concurrency — short queue timeout
  queue_target: 2_000,
  queue_interval: 1_000,
  timeout: 15_000

# Phase 8.20 — removed `prepare: :unnamed`. The unnamed override disables
# Postgres prepared-statement caching (one plan per query), costing a
# planner round-trip per repeated query. Ecto's default (named prepared
# statements) is the right choice when talking straight to Postgres.
# If we ever sit behind PgBouncer in transaction mode (which can't share
# prepared statements across pooled server conns), re-enable this only
# for that deployment via an env-driven branch:
#   if System.get_env("PGBOUNCER_MODE") == "transaction" do
#     config :social_service, SocialService.Repo, prepare: :unnamed
#   end
config :social_service, SocialServiceWeb.Endpoint,
  http: [
    ip: {0, 0, 0, 0},
    port: port,
    # num_acceptors at 1.2KB each = ~12KB idle cost
    thousand_island_options: [num_acceptors: 10]
  ],
  secret_key_base: secret_key_base,
  server: true

config :logger, level: :info
