import Config

config :matchmaking_service, MatchmakingServiceWeb.Endpoint, adapter: Bandit.PhoenixAdapter,
  http: [
    ip: {127, 0, 0, 1},
    port: 4004,
    thousand_island_options: [
      num_acceptors: 100
    ]
  ],
  check_origin: false,
  code_reloader: true,
  debug_errors: true,
  secret_key_base: "dev_secret_key_base_matchmaking_service_at_least_64_chars_long_replace_in_production",
  render_errors: [view: MatchmakingServiceWeb.ErrorView, accepts: ~w(json), layout: false],
  pubsub_server: MatchmakingService.PubSub,
  auth_service_url: "http://localhost:4001"
