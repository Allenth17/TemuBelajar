defmodule TemuBelajar.Repo do
  use Ecto.Repo,
    otp_app: :temubelajar,
    adapter: Ecto.Adapters.Postgres
end
