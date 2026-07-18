defmodule AuthService.Accounts.Session do
  use Ecto.Schema
  import Ecto.Changeset

  @primary_key {:token, :string, []}

  schema "sessions" do
    field(:email, :string)
    field(:expired_at, :utc_datetime)
    # Phase 2.4 — explicit revocation flag. When non-nil the session is
    # revoked (logout, security event, admin force-expire) and must no
    # longer resolve to a user, even before `expired_at`. Old cleanup
    # paths that deleted the row are unchanged; new revocation paths set
    # this timestamp and let the row persist as an audit trail until the
    # periodic cleanup_expired_sessions sweeper reaps expired rows.
    field(:revoked_at, :utc_datetime)

    timestamps(type: :utc_datetime, updated_at: false)
  end

  def changeset(session, attrs) do
    session
    |> cast(attrs, [:token, :email, :expired_at, :revoked_at])
    |> validate_required([:token, :email, :expired_at])
  end
end
