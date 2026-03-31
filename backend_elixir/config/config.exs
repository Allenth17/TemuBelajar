import Config

config :temubelajar,
  ecto_repos: [TemuBelajar.Repo]

config :temubelajar, TemuBelajarWeb.Endpoint,
  url: [host: "localhost"],
  adapter: Bandit.PhoenixAdapter,
  render_errors: [
    formats: [json: TemuBelajarWeb.ErrorJSON],
    layout: false
  ],
  pubsub_server: TemuBelajar.PubSub,
  live_view: [signing_salt: "temubelajar_salt"]

# Mailer credentials dikonfigurasi di runtime.exs dari env vars
config :temubelajar, TemuBelajar.Mailer, adapter: Swoosh.Adapters.SMTP

# Swoosh gunakan Finch (bukan Hackney default)
config :swoosh, :api_client, Swoosh.ApiClient.Finch
config :swoosh, :finch_name, TemuBelajar.Finch

config :logger, :console,
  format: "$time $metadata[$level] $message\n",
  metadata: [:request_id]

config :phoenix, :json_library, Jason

import_config "#{config_env()}.exs"
