import Config

# Phase 8.14 — prod-specific static config for the api_gateway.
#
# HTTPS/TLS wiring for the public listener lives in `config/runtime.exs`
# (inside the `config_env() == :prod` block) rather than here, because
# Phoenix runs `runtime.exs` AFTER this file at boot and the TLS cert /
# port are runtime secrets (env vars). Keeping the HTTPS block there means
# env-driven toggles (ENABLE_HTTPS, TLS_CERT_PATH, TLS_KEY_PATH,
# HTTPS_PORT, DISABLE_HTTP_AFTER_TLS) take effect on release boot.
#
# This prod.exs intentionally stays minimal so `config/config.exs`'s
# `import_config "#{config_env()}.exs"` resolves cleanly under MIX_ENV=prod.
