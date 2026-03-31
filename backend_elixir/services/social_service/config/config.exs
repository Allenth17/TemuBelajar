import Config

config :social_service,
  ecto_repos: [SocialService.Repo]

import_config "#{config_env()}.exs"
