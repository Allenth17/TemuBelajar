defmodule AuthServiceWeb.AuthController do
  use Phoenix.Controller, formats: [:json]
  import Plug.Conn
  alias AuthService.Accounts

  # POST /api/register
  def register(conn, params) do
    case Accounts.register_user(params) do
      {:ok, :registered} ->
        json(conn, %{success: true, message: "Kode OTP dikirim ke email"})

      {:error, :invalid_campus_email} ->
        conn
        |> put_status(400)
        |> json(%{error: "Email bukan dari kampus yang diizinkan"})

      {:error, :email_taken} ->
        conn
        |> put_status(409)
        |> json(%{error: "Email sudah terdaftar"})

      {:error, :username_taken} ->
        conn
        |> put_status(409)
        |> json(%{error: "Username sudah dipakai"})

      {:error, changeset} ->
        errors = format_changeset_errors(changeset)
        conn |> put_status(422) |> json(%{error: errors})
    end
  end

  # POST /api/verify-otp
  def verify_otp(conn, %{"email" => email, "otp" => otp}) do
    case Accounts.verify_otp(email, otp) do
      {:ok, :verified} ->
        json(conn, %{message: "OTP berhasil diverifikasi"})

      {:error, :not_found} ->
        conn |> put_status(404) |> json(%{error: "Email tidak ditemukan"})

      {:error, :otp_expired} ->
        conn |> put_status(410) |> json(%{error: "OTP kadaluarsa, silakan daftar ulang"})

      {:error, :wrong_otp} ->
        conn |> put_status(400) |> json(%{error: "OTP salah"})
    end
  end

  # POST /api/resend-otp
  def resend_otp(conn, %{"email" => email}) do
    case Accounts.resend_otp(email) do
      {:ok, :sent} ->
        json(conn, %{message: "OTP baru telah dikirim"})

      {:error, :not_found} ->
        conn |> put_status(404) |> json(%{error: "Email tidak ditemukan"})

      {:error, :already_verified} ->
        conn |> put_status(400) |> json(%{error: "Email sudah diverifikasi"})
    end
  end

  # POST /api/login
  def login(conn, params) do
    # Support both "email" and "email_or_username" parameters
    id = Map.get(params, "email_or_username") || Map.get(params, "email")
    pw = Map.get(params, "password")

    case Accounts.login(id, pw) do
      {:ok, token} ->
        json(conn, %{token: token, expires_in: "15 hari"})

      {:error, :not_found} ->
        conn |> put_status(404) |> json(%{error: "Email/Username tidak ditemukan"})

      {:error, :not_verified} ->
        conn |> put_status(403) |> json(%{error: "Email belum diverifikasi"})

      {:error, :wrong_password} ->
        conn |> put_status(401) |> json(%{error: "Password salah"})
    end
  end

  # POST /api/logout (requires auth)
  def logout(conn, _params) do
    token =
      case get_req_header(conn, "authorization") do
        ["Bearer " <> t] -> t
        _ -> nil
      end

    case Accounts.logout(token) do
      {:ok, :logged_out} ->
        json(conn, %{message: "Logged out successfully"})

      {:error, :invalid_token} ->
        conn |> put_status(401) |> json(%{error: "Invalid session token"})
    end
  end

  # GET /api/me (requires auth)
  def me(conn, _params) do
    token =
      case get_req_header(conn, "authorization") do
        ["Bearer " <> t] -> t
        _ -> nil
      end

    user = Accounts.get_user_by_token(token)

    case user do
      {:ok, u} ->
        json(conn, %{
          email: u.email,
          name: u.name,
          username: u.username,
          phone: u.phone,
          university: u.university,
          verified: u.verified,
          last_login: u.last_login
        })

      {:error, _} ->
        conn |> put_status(401) |> json(%{error: "Session invalid or expired"})
    end
  end

  # DELETE /api/expired-sessions
  def cleanup_sessions(conn, _params) do
    {:ok, count} = Accounts.cleanup_expired_sessions()
    json(conn, %{message: "#{count} sessions cleaned up"})
  end

  # GET /api/verify-token?token=... (internal use)
  def verify_token(conn, %{"token" => token}) do
    case Accounts.get_user_by_token(token) do
      {:ok, user} ->
        json(conn, %{valid: true, email: user.email, university: user.university})

      {:error, _} ->
        conn |> put_status(401) |> json(%{valid: false})
    end
  end

  # Helper function to format changeset errors
  defp format_changeset_errors(changeset) do
    Ecto.Changeset.traverse_errors(changeset, fn {msg, opts} ->
      Enum.reduce(opts, msg, fn {key, value}, acc ->
        String.replace(acc, "%{#{key}}", to_string(value))
      end)
    end)
  end
end
