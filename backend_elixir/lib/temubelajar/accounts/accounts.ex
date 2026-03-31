defmodule TemuBelajar.Accounts do
  @moduledoc """
  Accounts context — auth: register, OTP, login, logout.
  """
  import Ecto.Query
  alias TemuBelajar.Repo
  alias TemuBelajar.Accounts.{User, Session}
  alias TemuBelajar.Mailer
  alias TemuBelajar.Mailer.Email

  @otp_expiry_minutes 2
  @session_days 15

  # Helper function untuk async/sync berdasarkan environment
  defp run_async(fun) do
    if Mix.env() == :test do
      # Di test environment, jalankan secara synchronous
      fun.()
    else
      # Di production/development, jalankan secara asynchronous
      Task.start(fn ->
        try do
          fun.()
        rescue
          _ -> :ok
        end
      end)
    end
  end

  # Helper function untuk async/sync untuk email (selalu sync di test)
  defp run_async_email(fun) do
    if Mix.env() == :test do
      # Di test environment, jalankan secara synchronous
      fun.()
    else
      # Di production/development, jalankan secara asynchronous
      Task.start(fn ->
        try do
          fun.()
        rescue
          _ -> :ok
        end
      end)
    end
  end

  # ──────────────────────────── Campus email validation ────────────────────────

  @email_regex ~r/^[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.(ac\.id|com)$/

  def valid_campus_email?(email) do
    Regex.match?(@email_regex, email)
  end

  # ──────────────────────────── Register ───────────────────────────────────────

  def register_user(attrs) do
    email = Map.get(attrs, "email") || Map.get(attrs, :email)
    username = attrs["username"] || attrs[:username]

    cond do
      not valid_campus_email?(email) ->
        {:error, :invalid_campus_email}

      user_exists?(email) ->
        {:error, :email_taken}

      username_taken?(username) ->
        {:error, :username_taken}

      true ->
        otp = generate_otp()
        now = DateTime.utc_now() |> DateTime.truncate(:second)

        password = attrs["password"] || attrs[:password]
        # Use cost factor 8 for faster hashing (default is 12)
        password_hash = Bcrypt.hash_pwd_salt(password, log_rounds: 8)

        user_attrs = %{
          email: email,
          username: username,
          name: attrs["name"] || attrs[:name],
          phone: attrs["phone"] || attrs[:phone],
          university: attrs["university"] || attrs[:university],
          password_hash: password_hash,
          otp: otp,
          otp_created_at: now,
          verified: false
        }

        case Repo.insert(User.registration_changeset(%User{}, user_attrs)) do
          {:ok, _user} ->
            run_async_email(fn ->
              Email.send_otp(email, otp) |> Mailer.deliver()
            end)
            {:ok, :registered}

          {:error, changeset} ->
            {:error, changeset}
        end
    end
  end

  # ──────────────────────────── Verify OTP ─────────────────────────────────────

  def verify_otp(email, otp_input) do
    case Repo.get(User, email) do
      nil ->
        {:error, :not_found}

      user ->
        otp_ts = user.otp_created_at || user.inserted_at
        now = DateTime.utc_now()
        expired? = DateTime.diff(now, otp_ts, :minute) >= @otp_expiry_minutes

        cond do
          not user.verified and expired? ->
            # Delete expired user asynchronously
            run_async(fn -> Repo.delete!(user) end)
            {:error, :otp_expired}

          user.otp != otp_input ->
            {:error, :wrong_otp}

          true ->
            # Update user asynchronously
            run_async(fn ->
              user
              |> User.otp_changeset(%{
                verified: true,
                otp: nil,
                otp_created_at: nil,
                last_login: DateTime.utc_now() |> DateTime.truncate(:second)
              })
              |> Repo.update!()
            end)

            {:ok, :verified}
        end
    end
  end

  # ──────────────────────────── Resend OTP ─────────────────────────────────────

  def resend_otp(email) do
    case Repo.get(User, email) do
      nil -> {:error, :not_found}
      %User{verified: true} -> {:error, :already_verified}
      user ->
        otp = generate_otp()
        now = DateTime.utc_now() |> DateTime.truncate(:second)

        # Update OTP asynchronously
        run_async(fn ->
          user
          |> User.otp_changeset(%{otp: otp, otp_created_at: now})
          |> Repo.update!()
        end)

        # Send email asynchronously
        run_async_email(fn ->
          Email.send_otp(email, otp) |> Mailer.deliver()
        end)
        {:ok, :sent}
    end
  end

  # ──────────────────────────── Login ──────────────────────────────────────────

  def login(email_or_username, password) do
    # Try email first (faster with primary key)
    user = Repo.get(User, email_or_username)
    
    # If not found by email, try username (uses index)
    user = if is_nil(user) do
      Repo.one(from u in User, where: u.username == ^email_or_username)
    else
      user
    end

    cond do
      is_nil(user) -> {:error, :not_found}
      not user.verified -> {:error, :not_verified}
      not Bcrypt.verify_pass(password, user.password_hash) -> {:error, :wrong_password}
      true ->
        # update last_login asynchronously
        run_async(fn ->
          now = DateTime.utc_now() |> DateTime.truncate(:second)
          user
          |> User.otp_changeset(%{last_login: now})
          |> Repo.update!()
        end)

        token = generate_token()
        expired_at =
          DateTime.utc_now()
          |> DateTime.add(@session_days * 86_400, :second)
          |> DateTime.truncate(:second)

        Repo.insert!(%Session{token: token, email: user.email, expired_at: expired_at})
        {:ok, token}
    end
  end

  # ──────────────────────────── Logout ─────────────────────────────────────────

  def logout(token) do
    case Repo.get(Session, token) do
      nil -> {:error, :invalid_token}
      session ->
        Repo.delete!(session)
        {:ok, :logged_out}
    end
  end

  # ──────────────────────────── Get user by token ───────────────────────────────

  def get_user_by_token(token) do
    now = DateTime.utc_now()

    session =
      Repo.one(
        from s in Session,
          where: s.token == ^token and s.expired_at > ^now
      )

    case session do
      nil -> nil
      s -> Repo.get(User, s.email)
    end
  end

  def get_email_by_token(token) do
    now = DateTime.utc_now()

    Repo.one(
      from s in Session,
        where: s.token == ^token and s.expired_at > ^now,
        select: s.email
    )
  end

  # ──────────────────────────── Cleanup ────────────────────────────────────────

  def delete_expired_sessions do
    now = DateTime.utc_now()
    {count, _} = Repo.delete_all(from s in Session, where: s.expired_at < ^now)
    count
  end

  # ──────────────────────────── Private ────────────────────────────────────────

  defp user_exists?(email), do: not is_nil(Repo.get(User, email))

  defp username_taken?(username) do
    not is_nil(Repo.one(from u in User, where: u.username == ^username))
  end

  defp generate_otp do
    :rand.uniform(999_999)
    |> Integer.to_string()
    |> String.pad_leading(6, "0")
  end

  defp generate_token, do: :crypto.strong_rand_bytes(32) |> Base.url_encode64(padding: false)
end
