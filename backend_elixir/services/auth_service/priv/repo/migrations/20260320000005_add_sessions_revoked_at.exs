defmodule AuthService.Repo.Migrations.AddSessionsRevokedAt do
  use Ecto.Migration

  @moduledoc """
  Phase 2.4 — Revocation list.

  Shorten session TTL (handled in `Accounts.@session_days`, changed from
  15 to 7) is only half of the fix; the other half is a server-side
  revocation list so a stolen-but-not-yet-expired token can be
  invalidated before its natural expiry (logout from another device,
  admin force-logout, security incident response).

  We add a nullable `revoked_at` column to `sessions`. A row is
  considered revoked when `revoked_at IS NOT NULL`; the lookup helpers
  in `Accounts` (`get_user_by_token/1`, `get_email_by_token/1`) reject
  revoked rows even when `expired_at` is still in the future. An index on
  `revoked_at` lets the periodic cleanup sweeper cheaply find revoked
  rows past `expired_at` for pruning.
  """

  def up do
    alter table(:sessions) do
      add(:revoked_at, :utc_datetime, null: true)
    end

    create(index(:sessions, [:revoked_at]))
  end

  def down do
    drop(index(:sessions, [:revoked_at]))

    alter table(:sessions) do
      remove(:revoked_at)
    end
  end
end
