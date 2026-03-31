defmodule TemuBelajar.Accounts.Session do
  use Ecto.Schema
  import Ecto.Changeset

  @primary_key {:token, :string, []}

  schema "sessions" do
    field :email, :string
    field :expired_at, :utc_datetime

    timestamps(type: :utc_datetime, updated_at: false)
  end

  def changeset(session, attrs) do
    session
    |> cast(attrs, [:token, :email, :expired_at])
    |> validate_required([:token, :email, :expired_at])
  end
end
