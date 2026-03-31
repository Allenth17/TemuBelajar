defmodule UserService.UsersTest do
  @moduledoc """
  Tests for the UserService.Users context.

  Uses DataCase which provides a sandboxed Ecto repo — every test runs in a
  transaction that is rolled back afterwards, so tests remain fully isolated.
  """

  use UserService.DataCase, async: true

  alias UserService.Users
  alias UserService.Users.User

  # ── Factory helpers ──────────────────────────────────────────────────────────

  defp build_user_attrs(overrides \\ %{}) do
    base = %{
      email: "test_#{System.unique_integer([:positive])}@ui.ac.id",
      name: "Test User",
      username: "testuser_#{System.unique_integer([:positive])}",
      password_hash: "$2b$12$dummyhashforlocaltestingonly123"
    }
    Map.merge(base, overrides)
  end

  defp insert_user(overrides \\ %{}) do
    attrs = build_user_attrs(overrides)
    %User{}
    |> User.changeset(attrs)
    |> UserService.Repo.insert!()
  end

  # ── get_user/1 ──────────────────────────────────────────────────────────────

  describe "get_user/1" do
    test "returns the user when email exists" do
      user = insert_user()
      result = Users.get_user(user.email)
      assert result != nil
      assert result.email == user.email
    end

    test "returns nil when email does not exist" do
      assert Users.get_user("nonexistent@ui.ac.id") == nil
    end

    test "is case-sensitive (different case = different user)" do
      insert_user(email: "alice@ui.ac.id")
      assert Users.get_user("ALICE@ui.ac.id") == nil
    end
  end

  # ── get_user_by_username/1 ──────────────────────────────────────────────────

  describe "get_user_by_username/1" do
    test "returns user for a known username" do
      user = insert_user(username: "uniqueuser123")
      result = Users.get_user_by_username("uniqueuser123")
      assert result != nil
      assert result.email == user.email
    end

    test "returns nil for unknown username" do
      assert Users.get_user_by_username("ghost_user_xyz") == nil
    end
  end

  # ── update_user/2 ──────────────────────────────────────────────────────────

  describe "update_user/2" do
    test "updates name successfully" do
      user = insert_user()
      assert {:ok, updated} = Users.update_user(user.email, %{name: "New Name"})
      assert updated.name == "New Name"
    end

    test "updates university field" do
      user = insert_user()
      assert {:ok, updated} = Users.update_user(user.email, %{university: "ITB"})
      assert updated.university == "ITB"
    end

    test "returns error when email does not exist" do
      assert {:error, :not_found} = Users.update_user("ghost@ui.ac.id", %{name: "X"})
    end

    test "returns changeset error on invalid data" do
      user = insert_user()
      # name too long (assuming max 100 chars validation)
      long_name = String.duplicate("A", 300)
      result = Users.update_user(user.email, %{name: long_name})
      assert {:error, changeset} = result
      assert changeset.valid? == false
    end

    test "does not change other fields when updating one field" do
      user = insert_user(name: "Original Name", university: "UI")
      {:ok, updated} = Users.update_user(user.email, %{university: "ITB"})
      assert updated.name == "Original Name"
      assert updated.university == "ITB"
    end
  end

  # ── list_users/0 ────────────────────────────────────────────────────────────

  describe "list_users/1" do
    test "returns empty list when no users exist" do
      result = Users.list_users()
      assert result == []
    end

    test "returns all users up to default limit" do
      insert_user()
      insert_user()
      insert_user()
      assert length(Users.list_users()) == 3
    end

    test "respects the limit parameter" do
      Enum.each(1..5, fn _ -> insert_user() end)
      result = Users.list_users(3)
      assert length(result) <= 3
    end
  end

  # ── search_users/2 ──────────────────────────────────────────────────────────

  describe "search_users/2" do
    test "returns users matching name (case-insensitive)" do
      insert_user(name: "Budi Santoso")
      insert_user(name: "Rika Wulandari")
      result = Users.search_users("budi")
      assert length(result) == 1
      assert hd(result).name == "Budi Santoso"
    end

    test "returns users matching username" do
      insert_user(username: "budi_snt")
      insert_user(username: "rika_wln")
      result = Users.search_users("budi_snt")
      assert length(result) == 1
    end

    test "returns empty list when no match" do
      insert_user(name: "Budi Santoso")
      assert Users.search_users("zzznonexistent") == []
    end

    test "returns multiple matches" do
      insert_user(name: "Budi Santoso")
      insert_user(name: "Budi Prakoso")
      result = Users.search_users("budi")
      assert length(result) == 2
    end

    test "respects the limit parameter" do
      Enum.each(1..10, fn i -> insert_user(name: "Ahmad User #{i}") end)
      result = Users.search_users("Ahmad", 5)
      assert length(result) <= 5
    end

    test "partial match works" do
      insert_user(name: "Muhammad Arif Hidayat")
      result = Users.search_users("Arif")
      assert length(result) == 1
    end
  end
end
