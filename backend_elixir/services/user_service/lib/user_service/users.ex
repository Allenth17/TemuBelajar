defmodule UserService.Users do
  @moduledoc """
  The Users context.
  """

  import Ecto.Query, warn: false
  alias UserService.Repo
  alias UserService.Users.User

  @doc """
  Gets a user by email.
  """
  def get_user(email) do
    Repo.get(User, email)
  end

  @doc """
  Gets a user by username.
  """
  def get_user_by_username(username) do
    Repo.get_by(User, username: username)
  end

  @doc """
  Updates user profile.
  """
  def update_user(email, attrs) do
    case Repo.get(User, email) do
      nil ->
        {:error, :not_found}

      user ->
        changeset = User.changeset(user, attrs)

        case Repo.update(changeset) do
          {:ok, user} -> {:ok, user}
          {:error, changeset} -> {:error, changeset}
        end
    end
  end

  @doc """
  Lists all users (for admin purposes).
  """
  def list_users(limit \\ 100) do
    Repo.all(from u in User, limit: ^limit)
  end

  @doc """
  Searches users by name or username.
  """
  def search_users(query, limit \\ 20) do
    search_pattern = "%#{query}%"

    Repo.all(
      from u in User,
      where: ilike(u.name, ^search_pattern) or ilike(u.username, ^search_pattern),
      limit: ^limit
    )
  end
end
