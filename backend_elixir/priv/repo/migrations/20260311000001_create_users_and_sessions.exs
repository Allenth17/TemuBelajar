defmodule TemuBelajar.Repo.Migrations.CreateUsersAndSessions do
  use Ecto.Migration

  def change do
    # ── users ────────────────────────────────────────────────────────────
    create table(:users, primary_key: false) do
      add :email,          :string,  primary_key: true, null: false
      add :username,       :string,  null: false
      add :name,           :string,  null: false
      add :phone,          :string
      add :university,     :string
      add :password_hash,  :string,  null: false
      add :otp,            :string
      add :otp_created_at, :utc_datetime
      add :verified,       :boolean, default: false, null: false
      add :last_login,     :utc_datetime

      timestamps(type: :utc_datetime)
    end

    create unique_index(:users, [:username])

    # ── sessions ─────────────────────────────────────────────────────────
    create table(:sessions, primary_key: false) do
      add :token,      :string,      primary_key: true, null: false
      add :email,      :string,      null: false
      add :expired_at, :utc_datetime, null: false

      timestamps(type: :utc_datetime, updated_at: false)
    end

    create index(:sessions, [:email])
    create index(:sessions, [:expired_at])
  end
end
