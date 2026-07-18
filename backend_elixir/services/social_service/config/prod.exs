import Config

# Phase 8.16 / 8.20 — prod-specific static config for the social_service.
#
# Most prod tunables (DB pool, endpoint HTTP listener, CORS allowlist,
# internal_secret) live in `config/runtime.exs` inside the
# `config_env() == :prod` block — env-driven values are stored there
# because Phoenix runs `runtime.exs` AFTER this file at boot, so env-driven
# settings take effect on release boot. This prod.exs intentionally stays
# minimal so `config/config.exs`'s `import_config "#{config_env()}.exs"`
# resolves cleanly under MIX_ENV=prod.
