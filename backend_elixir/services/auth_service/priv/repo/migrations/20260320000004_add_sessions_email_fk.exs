defmodule AuthService.Repo.Migrations.AddSessionsEmailFk do
  use Ecto.Migration

  @moduledoc """
  Phase 7.13 — `sessions.email` references `users(email)` but was never
  declared as a foreign key. Without the FK, deleting a user leaves their
  session rows alive as orphans (expired sessions linger, the token still
  resolves, etc.). This migration adds the FK with `ON DELETE CASCADE`
  so deleting a user automatically removes their sessions.

  We use raw `ALTER TABLE … ADD CONSTRAINT … FOREIGN KEY (email)
  REFERENCES users(email) ON DELETE CASCADE` because the column already
  exists in the original migration — `references/2` inside `alter/2`
  would add a *new* column rather than FK-ing the existing one.
  """

  @table :sessions
  @fk_constraint :sessions_email_fkey

  def up do
    execute("""
    ALTER TABLE #{@table}
      ADD CONSTRAINT #{@fk_constraint}
      FOREIGN KEY (email) REFERENCES users(email)
      ON DELETE CASCADE
    """)
  end

  def down do
    execute("""
    ALTER TABLE #{@table}
      DROP CONSTRAINT IF EXISTS #{@fk_constraint}
    """)
  end
end
