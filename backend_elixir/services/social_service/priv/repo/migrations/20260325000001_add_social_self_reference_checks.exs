defmodule SocialService.Repo.Migrations.AddSocialSelfReferenceChecks do
  use Ecto.Migration

  @moduledoc """
  Phase 7.14 — The `friend_requests`, `blocks`, and `reports` tables
  in `20260325000000_create_social_tables.exs` were created with no
  `CHECK` guard against self-reference (the `follows` table got one —
  `no_self_follow` — but the other three did not). Without these each
  table silently accepts a row where the two email columns are equal —
  a user block-listing themselves, reporting themselves, or sending a
  friend request to themselves.

  This migration adds the three missing CHECK constraints, each named
  so the relationship to its table is obvious when introspecting the
  schema. Collision with existing names is avoided by using the
  table-prefixed `no_self_<noun>` family already used by `follows`.
  """

  def up do
    create(constraint(:friend_requests, :no_self_friend_request, check: "from_email <> to_email"))

    create(constraint(:blocks, :no_self_block, check: "blocker_email <> blocked_email"))

    create(constraint(:reports, :no_self_report, check: "reporter_email <> reported_email"))
  end

  def down do
    drop(constraint(:reports, :no_self_report))
    drop(constraint(:blocks, :no_self_block))
    drop(constraint(:friend_requests, :no_self_friend_request))
  end
end
