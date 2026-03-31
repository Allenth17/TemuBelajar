defmodule SocialService.Repo do
  use Ecto.Repo,
    otp_app: :social_service,
    adapter: Ecto.Adapters.Postgres
end
