defmodule TemuBelajar.AccountsTest do
  use TemuBelajar.DataCase, async: true
  alias TemuBelajar.Accounts
  alias TemuBelajar.Accounts.{User, Session}
  import Swoosh.TestAssertions

  @valid_attrs %{
    "email" => "student@ui.ac.id",
    "name" => "Student One",
    "username" => "student1",
    "phone" => "081234567890",
    "university" => "UI",
    "password" => "secret123"
  }

  describe "Changesets" do
    test "registration_changeset validation" do
      cs = User.registration_changeset(%User{}, %{})
      refute cs.valid?
      errors = errors_on(cs)
      assert errors.email == ["can't be blank"]

      cs_bad_email = User.registration_changeset(%User{}, %{@valid_attrs | "email" => "bad_format"})
      refute cs_bad_email.valid?
      assert errors_on(cs_bad_email).email == ["harus berupa email valid"]

      cs_bad_username = User.registration_changeset(%User{}, %{@valid_attrs | "username" => "s@bad"})
      refute cs_bad_username.valid?
      assert errors_on(cs_bad_username).username == ["hanya boleh huruf dan angka"]
    end
  end

  describe "register_user/1" do
    test "success with valid campus email" do
      assert {:ok, :registered} = Accounts.register_user(@valid_attrs)
      assert_email_sent(subject: "[TemuBelajar] Kode OTP Verifikasi")
    end

    test "success with ecampus.ut.ac.id email" do
      attrs = %{
        "email" => "056462043@ecampus.ut.ac.id",
        "name" => "Test User",
        "username" => "testuser056",
        "phone" => "081234567890",
        "university" => "UT",
        "password" => "secret123"
      }
      assert {:ok, :registered} = Accounts.register_user(attrs)
      assert_email_sent(subject: "[TemuBelajar] Kode OTP Verifikasi")
    end

    test "fails with non-campus email" do
      attrs = %{@valid_attrs | "email" => "test@gmail.org"}
      assert {:error, :invalid_campus_email} = Accounts.register_user(attrs)
    end

    test "fails when email is taken" do
      Accounts.register_user(@valid_attrs)
      assert {:error, :email_taken} = Accounts.register_user(%{@valid_attrs | "username" => "other"})
    end
    
    test "fails when username is taken" do
      Accounts.register_user(@valid_attrs)
      assert {:error, :username_taken} = Accounts.register_user(%{@valid_attrs | "email" => "new@itb.ac.id"})
    end
  end

  describe "verify_otp/2" do
    setup do
      Accounts.register_user(@valid_attrs)
      user = Repo.get(User, @valid_attrs["email"])
      %{user: user}
    end

    test "verifies correct OTP", %{user: user} do
      assert {:ok, :verified} = Accounts.verify_otp(user.email, user.otp)
      updated_user = Repo.get(User, user.email)
      assert updated_user.verified == true
      assert is_nil(updated_user.otp)
    end
    
    test "fails with wrong OTP", %{user: user} do
      assert {:error, :wrong_otp} = Accounts.verify_otp(user.email, "000000")
      refute Repo.get(User, user.email).verified
    end

    test "fails if user not found" do
      assert {:error, :not_found} = Accounts.verify_otp("nobody@ui.ac.id", "123456")
    end

    test "fails and deletes if expired and unverified", %{user: user} do
      old_ts = DateTime.utc_now() |> DateTime.add(-180, :second) # > 2 minutes
      user |> User.otp_changeset(%{otp_created_at: old_ts, inserted_at: old_ts}) |> Repo.update!()
      
      assert {:error, :otp_expired} = Accounts.verify_otp(user.email, user.otp)
      assert is_nil(Repo.get(User, user.email))
    end
  end

  describe "resend_otp/1" do
    setup do
      Accounts.register_user(@valid_attrs)
      user = Repo.get(User, @valid_attrs["email"])
      %{user: user}
    end

    test "success generates new OTP and sends", %{user: user} do
      assert {:ok, :sent} = Accounts.resend_otp(user.email)
      updated_user = Repo.get(User, user.email)
      assert updated_user.otp != user.otp
      assert_email_sent()
    end

    test "fails if already verified", %{user: user} do
      Accounts.verify_otp(user.email, user.otp)
      assert {:error, :already_verified} = Accounts.resend_otp(user.email)
    end

    test "fails if not found" do
      assert {:error, :not_found} = Accounts.resend_otp("nobody@ui.ac.id")
    end
  end

  describe "login/2 and logout/1" do
    setup do
      Accounts.register_user(@valid_attrs)
      user = Repo.get(User, @valid_attrs["email"])
      %{user: user}
    end

    test "fails if not verified", %{user: user} do
      assert {:error, :not_verified} = Accounts.login(user.email, "secret123")
    end

    test "fails with wrong password", %{user: user} do
      Accounts.verify_otp(user.email, user.otp)
      assert {:error, :wrong_password} = Accounts.login(user.email, "wrong")
    end

    test "success returning token", %{user: user} do
      Accounts.verify_otp(user.email, user.otp)
      assert {:ok, token} = Accounts.login(user.email, "secret123")
      assert String.length(token) > 20
      
      session = Repo.one(Session)
      assert session.token == token
    end

    test "logout deletes session", %{user: user} do
      Accounts.verify_otp(user.email, user.otp)
      {:ok, token} = Accounts.login(user.email, "secret123")
      assert {:ok, :logged_out} = Accounts.logout(token)
      assert is_nil(Repo.get(Session, token))
    end
  end

  describe "session extraction/cleanup" do
    setup do
      Accounts.register_user(@valid_attrs)
      user = Repo.get(User, @valid_attrs["email"])
      Accounts.verify_otp(user.email, user.otp)
      {:ok, token} = Accounts.login(user.email, "secret123")
      %{user: user, token: token}
    end

    test "get_user_by_token", %{user: _, token: token} do
      u = Accounts.get_user_by_token(token)
      assert u.email == @valid_attrs["email"]
    end

    test "get_email_by_token", %{user: _, token: token} do
      assert Accounts.get_email_by_token(token) == @valid_attrs["email"]
    end

    test "delete_expired_sessions", %{token: token} do
      assert Accounts.delete_expired_sessions() == 0
      
      old_ts = DateTime.utc_now() |> DateTime.add(-86_400, :second) |> DateTime.truncate(:second)
      session = Repo.get(Session, token)
      session |> Ecto.Changeset.change(%{expired_at: old_ts}) |> Repo.update!()
      
      assert Accounts.delete_expired_sessions() == 1
      assert is_nil(Accounts.get_user_by_token(token))
    end
  end
end
