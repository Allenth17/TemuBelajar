defmodule UserService.Users.User do
  use Ecto.Schema
  import Ecto.Changeset

  @primary_key {:email, :string, autogenerate: false}

  # Only profile-facing fields live here. Auth fields (password_hash, otp,
  # otp_created_at, verified) are owned by AuthService — mirroring them in
  # this service was an account-takeover vector (Phase 0.10 / 5.16). If the
  # legacy column still exists in this DB it is ignored on every read/write.
  #
  # @derive must precede defstruct/schema — that's where the Jason encoder
  # is wired up. The whitelist below prevents a future `json(conn, user)`
  # refactor from leaking any auth-field leaks that might be re-added to
  # the schema later.
  @derive {Jason.Encoder,
           only: [
             :email,
             :name,
             :username,
             :phone,
             :university,
             :major,
             :bio,
             :avatar_url,
             :last_login
           ]}

  schema "users" do
    field(:name, :string)
    field(:username, :string)
    field(:phone, :string)
    field(:university, :string)
    field(:major, :string)
    field(:bio, :string)
    field(:avatar_url, :string)
    field(:last_login, :utc_datetime)

    timestamps(type: :utc_datetime)
  end

  @permitted [:name, :username, :phone, :university, :major, :bio, :avatar_url]
  @required [:name, :username]

  def changeset(user, attrs) do
    user
    |> cast(attrs, @permitted)
    |> validate_required(@required)
    |> validate_length(:username, min: 3, max: 30)
    |> validate_format(:username, ~r/^[a-zA-Z0-9_]+$/)
    |> validate_length(:bio, max: 300)
    |> validate_length(:avatar_url, max: 500)
    |> unique_constraint(:username)
  end
end
