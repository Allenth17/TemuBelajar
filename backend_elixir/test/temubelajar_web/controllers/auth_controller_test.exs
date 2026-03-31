defmodule TemuBelajarWeb.AuthControllerTest do
  use TemuBelajarWeb.ConnCase
  import Swoosh.TestAssertions
  alias TemuBelajar.Accounts

  @valid_attrs %{
    "email" => "student@itb.ac.id",
    "name" => "Student Two",
    "username" => "student2",
    "phone" => "081234567891",
    "university" => "ITB",
    "password" => "password123"
  }

  setup %{conn: conn} do
    {:ok, conn: put_req_header(conn, "accept", "application/json")}
  end

  describe "POST /api/register" do
    test "registers user and sends OTP", %{conn: conn} do
      conn = post(conn, "/api/register", @valid_attrs)
      assert json_response(conn, 200)["success"] == true
      assert_email_sent()
    end

    test "fails with non-campus email", %{conn: conn} do
      conn = post(conn, "/api/register", %{@valid_attrs | "email" => "test@yahoo.org"})
      assert json_response(conn, 400)["error"] == "Email bukan dari kampus yang diizinkan"
    end
    
    test "fails with invalid data", %{conn: conn} do
      conn = post(conn, "/api/register", %{@valid_attrs | "username" => "s@1"})
      assert %{"error" => %{"username" => ["hanya boleh huruf dan angka"]}} = json_response(conn, 422)
    end
  end

  describe "OTP endpoints" do
    setup %{conn: _conn} do
      Accounts.register_user(@valid_attrs)
      user = TemuBelajar.Repo.get(Accounts.User, @valid_attrs["email"])
      %{user: user}
    end

    test "POST /api/verify-otp", %{conn: conn, user: user} do
      res = post(conn, "/api/verify-otp", %{"email" => user.email, "otp" => user.otp})
      assert json_response(res, 200)["message"] == "OTP berhasil diverifikasi"
      
      # Now it should be able to resend-otp and fail because verified
      res2 = post(conn, "/api/resend-otp", %{"email" => user.email})
      assert json_response(res2, 400)["error"] == "Email sudah diverifikasi"
    end

    test "POST /api/resend-otp success", %{conn: conn, user: user} do
      res = post(conn, "/api/resend-otp", %{"email" => user.email})
      assert json_response(res, 200)["message"] == "OTP baru telah dikirim"
    end
  end

  describe "Auth endpoints" do
    setup %{conn: _conn} do
      Accounts.register_user(@valid_attrs)
      user = TemuBelajar.Repo.get(Accounts.User, @valid_attrs["email"])
      Accounts.verify_otp(user.email, user.otp)
      {:ok, token} = Accounts.login(user.email, "password123")
      %{user: user, token: token}
    end

    test "POST /api/login", %{conn: conn, user: user} do
      res = post(conn, "/api/login", %{"email_or_username" => user.username, "password" => "password123"})
      assert %{"token" => _t, "expires_in" => _} = json_response(res, 200)
      
      res_fail = post(conn, "/api/login", %{"email_or_username" => user.email, "password" => "wrong"})
      assert json_response(res_fail, 401)["error"] == "Password salah"
    end

    test "GET /api/me success", %{conn: conn, token: token, user: user} do
      conn = put_req_header(conn, "authorization", "Bearer #{token}")
      res = get(conn, "/api/me")
      assert json_response(res, 200)["email"] == user.email
    end
    
    test "GET /api/me fails without token", %{conn: conn} do
      res = get(conn, "/api/me")
      assert json_response(res, 401)["error"] == "Token tidak valid atau kadaluarsa"
    end

    test "POST /api/logout", %{conn: conn, token: token} do
      conn_auth = put_req_header(conn, "authorization", "Bearer #{token}")
      res = post(conn_auth, "/api/logout")
      assert json_response(res, 200)["message"] == "Logged out successfully"
      
      # Token is no longer valid
      res2 = get(conn_auth, "/api/me")
      assert json_response(res2, 401)["error"] == "Token tidak valid atau kadaluarsa"
    end
    
    test "DELETE /api/expired-sessions", %{conn: conn, token: token} do
      conn_auth = put_req_header(conn, "authorization", "Bearer #{token}")
      res = delete(conn_auth, "/api/expired-sessions")
      assert json_response(res, 200)["message"] =~ "expired sessions dibersihkan"
    end
  end
end
