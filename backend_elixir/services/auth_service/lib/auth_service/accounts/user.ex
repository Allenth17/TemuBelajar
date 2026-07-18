defmodule AuthService.Accounts.User do
  use Ecto.Schema
  import Ecto.Changeset

  @primary_key {:email, :string, []}
  @derive {Jason.Encoder,
           only: [:email, :username, :name, :phone, :university, :verified, :last_login]}

  schema "users" do
    field(:otp, :string)
    field(:verified, :boolean, default: false)
    field(:password_hash, :string)
    field(:name, :string)
    field(:phone, :string)
    field(:university, :string)
    field(:username, :string)
    field(:otp_created_at, :utc_datetime)
    field(:last_login, :utc_datetime)

    timestamps(type: :utc_datetime)
  end

  # Phase 2.9 — structural email-shape regex. The richer campus-domain
  # validation lives in `Accounts.valid_campus_email?/1` (ac.id +
  # fake-domain rejection), but the changeset still validates shape so a
  # hand-rolled `Repo.insert(User, ...)` outside `Accounts.register_user/1`
  # can't bypass the floor. Rejects `User@`, `localhost`, `@@`, etc.
  @email_shape_regex ~r/^[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}$/

  @doc "Changeset untuk registrasi user baru"
  def registration_changeset(user, attrs) do
    user
    |> cast(attrs, [
      :email,
      :username,
      :name,
      :phone,
      :university,
      :password_hash,
      :otp,
      :otp_created_at
    ])
    |> validate_required([:email, :username, :name, :phone, :university, :password_hash])
    # Phase 2.9 — replace `validate_format(:email, ~r/@/, ...)` with the
    # stricter shape regex above. The context layer adds the campus
    # domain check; the changeset here is the floor.
    |> validate_format(:email, @email_shape_regex, message: "harus berupa email valid")
    |> validate_length(:username, min: 3, max: 20)
    |> validate_format(:username, ~r/^[a-zA-Z0-9]+$/, message: "hanya boleh huruf dan angka")
    # Phase 7.22 — drop the `name: :users_pkey` arg. Ecto auto-detects
    # the unique index for `:email` (the PK counts as a unique
    # constraint) and emits `{:email, "already in use"}` no matter what
    # the constraint is physically named. Coupling the changeset to the
    # PK name meant renaming the PK in a future migration would silently
    # stop surfacing the "email already taken" error.
    |> unique_constraint(:email)
    |> unique_constraint(:username)
  end

  @doc "Changeset untuk update OTP"
  def otp_changeset(user, attrs) do
    user
    |> cast(attrs, [:otp, :otp_created_at, :verified, :last_login])
  end
end
