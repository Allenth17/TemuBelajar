defmodule AuthService.Accounts.User do
  use Ecto.Schema
  import Ecto.Changeset

  @primary_key {:email, :string, []}
  @derive {Jason.Encoder, only: [:email, :username, :name, :phone, :university, :verified, :last_login]}

  schema "users" do
    field :otp, :string
    field :verified, :boolean, default: false
    field :password_hash, :string
    field :name, :string
    field :phone, :string
    field :university, :string
    field :username, :string
    field :otp_created_at, :utc_datetime
    field :last_login, :utc_datetime

    timestamps(type: :utc_datetime)
  end

  @doc "Changeset untuk registrasi user baru"
  def registration_changeset(user, attrs) do
    user
    |> cast(attrs, [:email, :username, :name, :phone, :university, :password_hash, :otp, :otp_created_at])
    |> validate_required([:email, :username, :name, :phone, :university, :password_hash])
    |> validate_format(:email, ~r/@/, message: "harus berupa email valid")
    |> validate_length(:username, min: 3, max: 20)
    |> validate_format(:username, ~r/^[a-zA-Z0-9]+$/, message: "hanya boleh huruf dan angka")
    |> unique_constraint(:email, name: :users_pkey)
    |> unique_constraint(:username)
  end

  @doc "Changeset untuk update OTP"
  def otp_changeset(user, attrs) do
    user
    |> cast(attrs, [:otp, :otp_created_at, :verified, :last_login])
  end
end
