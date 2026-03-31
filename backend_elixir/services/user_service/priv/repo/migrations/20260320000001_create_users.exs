defmodule UserService.Repo.Migrations.CreateUsers do
  use Ecto.Migration

  def change do
    create table(:users, primary_key: false) do
      add :email, :string, primary_key: true
      add :name, :string, null: false
      add :username, :string, null: false
      add :phone, :string
      add :university, :string
      add :verified, :boolean, default: false, null: false
      add :password_hash, :string, null: false
      add :otp, :string
      add :otp_created_at, :utc_datetime
      add :last_login, :utc_datetime
      add :inserted_at, :utc_datetime, default: fragment("NOW()")
      add :updated_at, :utc_datetime, default: fragment("NOW()")
    end

    create unique_index(:users, [:username])
    create index(:users, [:verified])
    create index(:users, [:otp_created_at])
    create index(:users, [:last_login])
    create index(:users, [:verified, :otp_created_at])
  end
end
