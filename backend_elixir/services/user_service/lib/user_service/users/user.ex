defmodule UserService.Users.User do
  use Ecto.Schema
  import Ecto.Changeset

  @primary_key {:email, :string, autogenerate: false}
  schema "users" do
    field :name, :string
    field :username, :string
    field :phone, :string
    field :university, :string
    field :major, :string
    field :bio, :string
    field :avatar_url, :string
    field :verified, :boolean, default: false
    field :password_hash, :string
    field :otp, :string
    field :otp_created_at, :utc_datetime
    field :last_login, :utc_datetime

    timestamps(type: :utc_datetime)
  end

  def changeset(user, attrs) do
    user
    |> cast(attrs, [:name, :username, :phone, :university, :major, :bio, :avatar_url,
                    :verified, :password_hash, :otp, :otp_created_at, :last_login])
    |> validate_required([:name, :username])
    |> validate_length(:username, min: 3, max: 30)
    |> validate_format(:username, ~r/^[a-zA-Z0-9_]+$/)
    |> validate_length(:bio, max: 300)
    |> validate_length(:avatar_url, max: 500)
    |> unique_constraint(:username)
  end
end
