defmodule AuthService.AccountsTest do
  use AuthService.DataCase, async: true
  alias AuthService.Accounts

  describe "register_user/1" do
    test "registers a new user with valid data" do
      attrs = %{
        email: "test@ui.ac.id",
        username: "testuser",
        name: "Test User",
        phone: "081234567890",
        university: "UI",
        password: "password123"
      }

      assert {:ok, :registered} = Accounts.register_user(attrs)

      # Check user was created
      user = Repo.get_by(Accounts.User, email: "test@ui.ac.id")
      assert user
      assert user.username == "testuser"
      assert user.name == "Test User"
      assert user.verified == false
      assert user.otp
      assert user.otp_created_at
    end

    test "returns error for invalid campus email" do
      attrs = %{
        email: "test@gmail.org",
        username: "testuser",
        name: "Test User",
        phone: "081234567890",
        university: "UI",
        password: "password123"
      }

      assert {:error, :invalid_campus_email} = Accounts.register_user(attrs)
    end

    test "returns error for duplicate email" do
      attrs = %{
        email: "test@ui.ac.id",
        username: "testuser",
        name: "Test User",
        phone: "081234567890",
        university: "UI",
        password: "password123"
      }

      assert {:ok, :registered} = Accounts.register_user(attrs)
      assert {:error, :email_taken} = Accounts.register_user(attrs)
    end

    test "returns error for duplicate username" do
      attrs1 = %{
        email: "test1@ui.ac.id",
        username: "testuser",
        name: "Test User 1",
        phone: "081234567890",
        university: "UI",
        password: "password123"
      }

      attrs2 = %{
        email: "test2@ui.ac.id",
        username: "testuser",
        name: "Test User 2",
        phone: "081234567891",
        university: "UI",
        password: "password123"
      }

      assert {:ok, :registered} = Accounts.register_user(attrs1)
      assert {:error, :username_taken} = Accounts.register_user(attrs2)
    end
  end

  describe "verify_otp/2" do
    setup do
      attrs = %{
        email: "test@ui.ac.id",
        username: "testuser",
        name: "Test User",
        phone: "081234567890",
        university: "UI",
        password: "password123"
      }

      {:ok, :registered} = Accounts.register_user(attrs)
      user = Repo.get_by(Accounts.User, email: "test@ui.ac.id")
      %{user: user}
    end

    test "verifies user with correct OTP", %{user: user} do
      assert {:ok, :verified} = Accounts.verify_otp(user.email, user.otp)

      updated_user = Repo.get(Accounts.User, user.email)
      assert updated_user.verified == true
      assert updated_user.otp == nil
      assert updated_user.otp_created_at == nil
    end

    test "returns error for wrong OTP", %{user: user} do
      assert {:error, :wrong_otp} = Accounts.verify_otp(user.email, "000000")
    end

    test "returns error for expired OTP", %{user: user} do
      # Set OTP created_at to 10 minutes ago
      expired_time = DateTime.add(DateTime.utc_now(), -600, :second) |> DateTime.truncate(:second)
      user
      |> Ecto.Changeset.change(%{otp_created_at: expired_time})
      |> Repo.update!()

      assert {:error, :otp_expired} = Accounts.verify_otp(user.email, user.otp)
    end
  end

  describe "login/2" do
    setup do
      attrs = %{
        email: "test@ui.ac.id",
        username: "testuser",
        name: "Test User",
        phone: "081234567890",
        university: "UI",
        password: "password123"
      }

      {:ok, :registered} = Accounts.register_user(attrs)
      user = Repo.get_by(Accounts.User, email: "test@ui.ac.id")
      Accounts.verify_otp(user.email, user.otp)
      %{user: user}
    end

    test "logs in with correct email and password", %{user: user} do
      assert {:ok, token} = Accounts.login(user.email, "password123")
      assert token

      session = Repo.get_by(Accounts.Session, token: token)
      assert session.email == user.email
    end

    test "logs in with correct username and password", %{user: user} do
      assert {:ok, token} = Accounts.login(user.username, "password123")
      assert token
    end

    test "returns error for wrong password", %{user: user} do
      assert {:error, :wrong_password} = Accounts.login(user.email, "wrongpassword")
    end

    test "returns error for unverified user" do
      attrs = %{
        email: "test2@ui.ac.id",
        username: "testuser2",
        name: "Test User 2",
        phone: "081234567891",
        university: "UI",
        password: "password123"
      }

      {:ok, :registered} = Accounts.register_user(attrs)

      assert {:error, :not_verified} = Accounts.login("test2@ui.ac.id", "password123")
    end

    test "returns error for non-existent user" do
      assert {:error, :not_found} = Accounts.login("nonexistent@ui.ac.id", "password123")
    end
  end

  describe "logout/1" do
    setup do
      attrs = %{
        email: "test@ui.ac.id",
        username: "testuser",
        name: "Test User",
        phone: "081234567890",
        university: "UI",
        password: "password123"
      }

      {:ok, :registered} = Accounts.register_user(attrs)
      user = Repo.get_by(Accounts.User, email: "test@ui.ac.id")
      Accounts.verify_otp(user.email, user.otp)
      {:ok, token} = Accounts.login(user.email, "password123")
      %{token: token}
    end

    test "logs out with valid token", %{token: token} do
      assert {:ok, :logged_out} = Accounts.logout(token)

      session = Repo.get_by(Accounts.Session, token: token)
      assert session == nil
    end

    test "returns error for invalid token" do
      assert {:error, :invalid_token} = Accounts.logout("invalid_token")
    end
  end

  describe "get_user_by_token/1" do
    setup do
      attrs = %{
        email: "test@ui.ac.id",
        username: "testuser",
        name: "Test User",
        phone: "081234567890",
        university: "UI",
        password: "password123"
      }

      {:ok, :registered} = Accounts.register_user(attrs)
      user = Repo.get_by(Accounts.User, email: "test@ui.ac.id")
      Accounts.verify_otp(user.email, user.otp)
      {:ok, token} = Accounts.login(user.email, "password123")
      %{token: token, user: user}
    end

    test "returns user with valid token", %{token: token, user: user} do
      assert {:ok, returned_user} = Accounts.get_user_by_token(token)
      assert returned_user.email == user.email
      assert returned_user.username == user.username
    end

    test "returns error for invalid token" do
      assert {:error, :not_found} = Accounts.get_user_by_token("invalid_token")
    end

    test "returns error for expired token", %{token: token} do
      # Set session expired_at to past
      expired_time = DateTime.add(DateTime.utc_now(), -1, :day) |> DateTime.truncate(:second)
      session = Repo.get_by(Accounts.Session, token: token)
      session
      |> Ecto.Changeset.change(%{expired_at: expired_time})
      |> Repo.update!()

      assert {:error, :expired} = Accounts.get_user_by_token(token)
    end
  end

  describe "get_email_by_token/1" do
    setup do
      attrs = %{
        email: "test@ui.ac.id",
        username: "testuser",
        name: "Test User",
        phone: "081234567890",
        university: "UI",
        password: "password123"
      }

      {:ok, :registered} = Accounts.register_user(attrs)
      user = Repo.get_by(Accounts.User, email: "test@ui.ac.id")
      Accounts.verify_otp(user.email, user.otp)
      {:ok, token} = Accounts.login(user.email, "password123")
      %{token: token, user: user}
    end

    test "returns email with valid token", %{token: token, user: user} do
      assert Accounts.get_email_by_token(token) == user.email
    end

    test "returns nil for invalid token" do
      assert Accounts.get_email_by_token("invalid_token") == nil
    end
  end
end
