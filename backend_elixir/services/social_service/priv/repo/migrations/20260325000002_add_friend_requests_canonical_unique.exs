defmodule SocialService.Repo.Migrations.AddFriendRequestsCanonicalUnique do
  use Ecto.Migration

  @moduledoc """
  Phase 7.15 — `friend_requests` declares
  `unique_index([:from_email, :to_email])` but that only prevents two
  exact duplicates of the same `(from, to)` pair. Nothing stops Alice
  from sending Bob a request AND Bob from sending Alice a request in
  parallel — both rows pass the row-level unique constraint because the
  `(from_email, to_email)` tuples differ even though they reference the
  same unordered friendship.

  This migration adds a *canonical* unique index on
  `(LEAST(from_email, to_email), GREATEST(from_email, to_email))` so any
  `(A, B)` pair — regardless of sender order — collapses to the same
  index key, exactly blocking both halves of an A↔B duplicate.

  We add the index RAW via `CREATE UNIQUE INDEX IF NOT EXISTS` because
  Ecto 1.14's `create unique_index/2` doesn't accept expression
  arguments. The migration is idempotent: re-running against a cluster
  that already has this index (e.g. after a failed deploy) is a no-op.

  We do *not* drop the existing `friend_requests_pkey` row-level unique
  constraint — it remains the PK anchor and still defends against exact
  duplicate `(from_email, to_email)` tuples, while this new index adds
  the unordered-canonical guarantee.
  """

  @index_name :friend_requests_canonical_unique

  def up do
    execute("""
    CREATE UNIQUE INDEX IF NOT EXISTS #{@index_name}
      ON friend_requests (LEAST(from_email, to_email), GREATEST(from_email, to_email))
    """)
  end

  def down do
    execute("DROP INDEX IF EXISTS #{@index_name}")
  end
end
