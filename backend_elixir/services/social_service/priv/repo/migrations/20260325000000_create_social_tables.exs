defmodule SocialService.Repo.Migrations.CreateSocialTables do
  use Ecto.Migration

  def change do
    # ── Follows ──────────────────────────────────────────────────────────────
    create table(:follows, primary_key: false) do
      add :follower_email, :string, size: 255, null: false
      add :followee_email, :string, size: 255, null: false
      add :inserted_at, :utc_datetime, null: false, default: fragment("NOW()")
    end

    create constraint(:follows, :no_self_follow,
      check: "follower_email <> followee_email"
    )
    create unique_index(:follows, [:follower_email, :followee_email])
    # Fast lookup: "who follows X?" and "who does X follow?"
    create index(:follows, [:followee_email])
    create index(:follows, [:follower_email])

    # ── Friend Requests ───────────────────────────────────────────────────────
    create table(:friend_requests, primary_key: false) do
      add :from_email, :string, size: 255, null: false
      add :to_email, :string, size: 255, null: false
      add :status, :string, size: 20, null: false, default: "pending"
      add :inserted_at, :utc_datetime, null: false, default: fragment("NOW()")
    end

    create unique_index(:friend_requests, [:from_email, :to_email])
    create index(:friend_requests, [:to_email, :status])

    # ── Blocks ────────────────────────────────────────────────────────────────
    create table(:blocks, primary_key: false) do
      add :blocker_email, :string, size: 255, null: false
      add :blocked_email, :string, size: 255, null: false
      add :inserted_at, :utc_datetime, null: false, default: fragment("NOW()")
    end

    create unique_index(:blocks, [:blocker_email, :blocked_email])
    create index(:blocks, [:blocked_email])

    # ── Reports ───────────────────────────────────────────────────────────────
    create table(:reports) do
      add :reporter_email, :string, size: 255, null: false
      add :reported_email, :string, size: 255, null: false
      add :reason, :string, size: 50, null: false
      add :detail, :string, size: 500
      add :inserted_at, :utc_datetime, null: false, default: fragment("NOW()")
    end

    create index(:reports, [:reported_email])
  end
end
