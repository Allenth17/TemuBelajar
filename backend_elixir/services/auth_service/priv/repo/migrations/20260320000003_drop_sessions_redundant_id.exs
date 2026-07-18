defmodule AuthService.Repo.Migrations.DropSessionsRedundantId do
  use Ecto.Migration

  @moduledoc """
  Phase 7.12 — Repair the double primary-key defect in `sessions`.

  The original `20260320000002_create_session.exs` migration ran
  `create table(:sessions)` *without* `primary_key: false`, so Ecto
  implicitly added an `id BIGSERIAL` primary-key column alongside the
  intended `add :token, :string, primary_key: true`. The composite
  result is two primary-key declarations on the same table, which
  fails creation outright on real Postgres clusters that reject the
  double PK (and on tolerant test DBs the implicit `id` lingers as a
  redundant, unused PK that never matches the schema).

  This migration is idempotent: it probes whether the implicit `id`
  column actually exists before dropping it, so it works against both
  "the original migration barely succeeded" clusters (drop the
  redundant PK) and "the original migration failed entirely" clusters
  (the column was never created, so there's nothing to drop). In the
  latter case the original migration is still broken; the right
  long-term fix is to amend `20260320000002_create_sessions.exs` to
  use `create table(:sessions, primary_key: false)`, but per the Phase
  7 instructions we cannot retcon existing migrations, so this forward
  repair is the only mechanism available.
  """

  @doc """
  Drop the implicit `id` PK column if it is present (i.e. the original
  migration succeeded on a tolerant cluster). On clusters where the
  double-PK errored out the column already doesn't exist, so this is
  a no-op there.
  """
  def up do
    execute("""
    DO $$
    BEGIN
      IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'sessions' AND column_name = 'id'
      ) THEN
        ALTER TABLE sessions DROP COLUMN id;
      END IF;
    END $$;
    """)
  end

  def down do
    execute("""
    DO $$
    BEGIN
      IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'sessions' AND column_name = 'id'
      ) THEN
        ALTER TABLE sessions ADD COLUMN id BIGSERIAL PRIMARY KEY;
      END IF;
    END $$;
    """)
  end
end
