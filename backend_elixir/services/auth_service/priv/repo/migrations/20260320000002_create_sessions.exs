defmodule AuthService.Repo.Migrations.CreateSessions do
  use Ecto.Migration

  def change do
    create table(:sessions) do
      add :token, :string, primary_key: true
      add :email, :string, null: false
      add :expired_at, :utc_datetime, null: false
      add :inserted_at, :utc_datetime, default: fragment("NOW()")
    end

    create index(:sessions, [:email])
    create index(:sessions, [:expired_at])
  end
end
