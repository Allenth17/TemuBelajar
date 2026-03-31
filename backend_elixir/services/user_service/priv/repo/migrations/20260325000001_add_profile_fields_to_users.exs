defmodule UserService.Repo.Migrations.AddProfileFieldsToUsers do
  use Ecto.Migration

  def change do
    alter table(:users) do
      add :major, :string, size: 100
      add :avatar_url, :string, size: 500
      add :bio, :string, size: 300
    end

    # Index for matchmaking — common to query by university+major
    create index(:users, [:university, :major])
  end
end
