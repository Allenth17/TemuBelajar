defmodule AuthService.AccountsFixtures do
  @moduledoc """
  This module defines test helpers for creating
  entities via the `AuthService.Accounts` context.
  """

  alias AuthService.Accounts
  alias AuthService.Repo

  @doc """
  Generate a user.
  """
  def user_fixture(attrs \\ %{}) do
    {:ok, :registered} =
      attrs
      |> Enum.into(%{
        email: "user#{System.unique_integer([:positive])}@ui.ac.id",
        username: "user#{System.unique_integer([:positive])}",
        name: "Test User",
        password: "password123"
      })
      |> Accounts.register_user()

    user = Repo.get_by(Accounts.User, email: attrs[:email] || "user#{System.unique_integer([:positive])}@ui.ac.id")

    # Verify the user
    Accounts.verify_otp(user.email, user.otp)

    user
  end

  @doc """
  Generate a verified user with a valid session token.
  """
  def authenticated_user_fixture(attrs \\ %{}) do
    user = user_fixture(attrs)
    {:ok, token} = Accounts.login(user.email, "password123")
    %{user: user, token: token}
  end
end
