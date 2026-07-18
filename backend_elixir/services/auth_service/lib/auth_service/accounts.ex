defmodule AuthService.Accounts do
  @moduledoc """
  Accounts context — auth: register, OTP, login, logout.
  """

  import Ecto.Query
  alias AuthService.Repo
  alias AuthService.Accounts.{User, Session}
  alias AuthService.Mailer.Email

  @otp_expiry_minutes 2
  # Phase 2.4 — Shortened from 15 → 7 days. Combined with the new
  # revocation list (revoked_at on the sessions row) we no longer need
  # 15d tokens that can't be invalidated. Logout (or admin force-revoke)
  # sets revoked_at so the token stops resolving immediately, well ahead
  # of the natural 7d expiry.
  @session_days 7

  # Phase 0.8 / 2.2 — OTP brute-force hardening.
  @otp_max_attempts 5
  @otp_lockout_minutes 15
  @otp_resend_max 3
  @otp_resend_cooldown_minutes 10

  # ETS table for OTP rate-limit state (attempts, lockout, resend count).
  # Keyed by email. Patterns: {email, attempts, lockout_until, resend_count,
  # first_sent_at}. Avoids a migration and gives us O(1) reads.
  @rl_table :auth_otp_ratelimit

  # Helper function untuk async/sync berdasarkan environment
  defp run_async(fun) do
    if Mix.env() == :test do
      fun.()
    else
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
      fun.()
    else
      Task.start(fn ->
        try do
          fun.()
        rescue
          _ -> :ok
        end
      end)
    end
  end

  # ─── Rate-limit state management (Phase 0.8 / 2.2) ──────────────────────────

  defp ensure_rl_table do
    if :ets.whereis(@rl_table) == :undefined do
      :ets.new(@rl_table, [:named_table, :public, :set])
    end
  end

  defp rl_get(email) do
    ensure_rl_table()

    case :ets.lookup(@rl_table, email) do
      [{^email, attempts, lockout_until, resend_count, first_sent_at}] ->
        {attempts, lockout_until, resend_count, first_sent_at}

      [] ->
        {0, nil, 0, nil}
    end
  end

  defp rl_put(email, attempts, lockout_until, resend_count, first_sent_at) do
    ensure_rl_table()
    :ets.insert(@rl_table, {email, attempts, lockout_until, resend_count, first_sent_at})
  end

  defp rl_reset(email) do
    ensure_rl_table()
    :ets.delete(@rl_table, email)
  end

  # ──────────────────────────── Campus email validation ────────────────────────
  #
  # Phase 2.9 — the old regex was `~r/@/` *in the changeset* (user.ex)
  # while the context-layer validator (`valid_campus_email?/1`) used a
  # stricter pattern. Weakened means a user could pass register_user's
  # strict check but fail changeset changes or vice-versa. We now use a
  # single, sane regex at the context layer and the changeset delegates
  # to a structural email-shape regex (see user.ex).
  #
  # The pattern accepts the original ac.id / com TLDs the audit cared
  # about; `ac.id` matches Indonesian campus domains (ui.ac.id,
  # itb.ac.id) and `com` covers the legacy test/dev accounts. Fake
  # domains (`example.com`, `test.com`, `localhost`) are explicitly
  # rejected — Phase 2.9 said "reject obviously fake domains".
  #
  # Phase 2.8 — emails are downcased before this regex runs (see
  # `register_user/1` and `login/2`), so the local-part and domain can
  # be compared with a single canonical form. Don't add an `i` flag —
  # case-folding at the boundary is enough and lets us keep the explicit
  # lowercase charset here so a stray uppercase doesn't slip past.
  @email_regex ~r/^[a-z0-9._%+\-]+@[a-z0-9.\-]+\.(ac\.id|com)$/

  # Fake / placeholder domains we never accept, regardless of regex
  # shape match. `localhost` (no dot) is already rejected by the
  # regex; `example.com` and `test.com` are listed here because they
  # MATCH the regex shape but are obviously not real user mailboxes.
  @fake_domains ~w(example.com test.com localhost)

  @spec valid_campus_email?(binary()) :: boolean()
  def valid_campus_email?(email) when is_binary(email) do
    downcased = String.downcase(email)

    if Regex.match?(@email_regex, downcased) do
      domain =
        downcased
        |> String.split("@", parts: 2)
        |> List.last()

      domain not in @fake_domains
    else
      false
    end
  end

  def valid_campus_email?(_), do: false

  # Phase 2.8 — canonicalize an email to its lowercase form before any
  # DB lookup / insert. `User@ui.ac.id` and `user@ui.ac.id` resolve to
  # the same account; the email column PK in Postgres is case-sensitive.
  # Downcasing at the boundary (register/login) avoids `User@...` being
  # treated as distinct from `user@...` and avoids accidental twin
  # accounts created by users typing their email capitalised.
  defp canonical_email(nil), do: nil
  defp canonical_email(email) when is_binary(email), do: String.downcase(email)

  # ──────────────────────────── Register ───────────────────────────────────────

  def register_user(attrs) do
    # Phase 2.8 — canonicalize email to lowercase before any lookup /
    # insert so `User@ui.ac.id` and `user@ui.ac.id` are treated as the
    # same account (Postgres PK is case-sensitive by default).
    raw_email = Map.get(attrs, "email") || Map.get(attrs, :email)
    email = canonical_email(raw_email)
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
          # Phase 2.8 — store the canonical (lowercased) email.
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
              Email.send_otp(email, otp) |> AuthService.Mailer.deliver()
            end)

            {:ok, :registered}

          {:error, changeset} ->
            {:error, changeset}
        end
    end
  end

  # ──────────────────────────── Verify OTP ────────────────────────────────────

  def verify_otp(email, otp_input) do
    # Phase 2.8 — canonicalize to lowercase before the DB lookup so
    # callers that submit a capitalised email still resolve to the row
    # created with the lowercased form.
    email = canonical_email(email)

    case Repo.get(User, email) do
      nil ->
        # Phase 2.10 — dummy bcrypt verify so the not-found path takes
        # roughly the same wall-clock time as a real verify (anti-timing).
        Bcrypt.no_user_verify()
        {:error, :not_found}

      user ->
        # Phase 0.8 — check lockout before anything else.
        {attempts, lockout_until, _resend_count, _first_sent_at} = rl_get(email)
        now = DateTime.utc_now()

        lockout_active? = lockout_until != nil and DateTime.compare(now, lockout_until) == :lt

        cond do
          lockout_active? ->
            {:error, :locked_out}

          is_nil(user.otp_created_at) ->
            # Phase 2.5 — pending OTP must have a creation timestamp.
            # Falling back to inserted_at would let an OTP live 15 days.
            rl_reset(email)
            {:error, :otp_expired}

          DateTime.diff(now, user.otp_created_at, :minute) >= @otp_expiry_minutes ->
            # Phase 2.6 — delete the expired row synchronously so a
            # concurrent resend can't find it and re-activate.
            Repo.delete!(user)
            rl_reset(email)
            {:error, :otp_expired}

          user.otp != otp_input ->
            # Phase 0.8 — increment attempts; rotate OTP on wrong guess so
            # the same code can't be retried @otp_max_attempts times.
            new_attempts = attempts + 1

            if new_attempts >= @otp_max_attempts do
              lockout =
                DateTime.utc_now()
                |> DateTime.add(@otp_lockout_minutes * 60, :second)
                |> DateTime.truncate(:second)

              rl_put(email, new_attempts, lockout, 0, nil)

              # Rotate the OTP value so brute-forcing the in-DB code is
              # impossible after the attempt cap is hit.
              new_otp = generate_otp()
              now_ts = DateTime.utc_now() |> DateTime.truncate(:second)

              user
              |> User.otp_changeset(%{otp: new_otp, otp_created_at: now_ts})
              |> Repo.update!()
            else
              new_otp = generate_otp()
              now_ts = DateTime.utc_now() |> DateTime.truncate(:second)
              rl_put(email, new_attempts, lockout_until, 0, nil)

              user
              |> User.otp_changeset(%{otp: new_otp, otp_created_at: now_ts})
              |> Repo.update!()
            end

            {:error, :wrong_otp}

          true ->
            # Phase 5.34 — write synchronously so a racing login can't
            # observe a still-unverified row.
            user
            |> User.otp_changeset(%{
              verified: true,
              otp: nil,
              otp_created_at: nil,
              last_login: DateTime.utc_now() |> DateTime.truncate(:second)
            })
            |> Repo.update!()

            rl_reset(email)
            {:ok, :verified}
        end
    end
  end

  # ──────────────────────────── Resend OTP ────────────────────────────────────

  def resend_otp(email) do
    # Phase 2.8 — canonicalize before lookup.
    email = canonical_email(email)

    case Repo.get(User, email) do
      nil ->
        {:error, :not_found}

      user ->
        {attempts, lockout_until, resend_count, first_sent_at} = rl_get(email)
        now = DateTime.utc_now()

        # Phase 0.8 — respect lockout — even resend is rejected while locked.
        if lockout_until != nil and DateTime.compare(now, lockout_until) == :lt do
          {:error, :locked_out}
        else
          if user.verified do
            {:error, :already_verified}
          else
            # Phase 0.8 — only @otp_resend_max resends within the cooldown.
            resend_count = resend_count_for_window(resend_count, first_sent_at, now)

            if resend_count >= @otp_resend_max do
              {:error, :resend_cooldown}
            else
              otp = generate_otp()
              now_ts = DateTime.truncate(now, :second)

              # Write synchronously so the next verify_otp sees this OTP
              # immediately (Phase 5.34 extended to resend path).
              user
              |> User.otp_changeset(%{otp: otp, otp_created_at: now_ts})
              |> Repo.update!()

              run_async_email(fn ->
                Email.send_otp(email, otp) |> AuthService.Mailer.deliver()
              end)

              rl_put(email, attempts, lockout_until, resend_count + 1, now_ts)
              {:ok, :sent}
            end
          end
        end
    end
  end

  # Resend window of @otp_resend_cooldown_minutes — reset count if the
  # last resend was outside the window.
  defp resend_count_for_window(resend_count, first_sent_at, now) do
    case first_sent_at do
      nil ->
        0

      ts ->
        if DateTime.diff(now, ts, :minute) >= @otp_resend_cooldown_minutes do
          0
        else
          resend_count
        end
    end
  end

  # ──────────────────────────── Login ───────────────────────────────────────────

  def login(email_or_username, password) do
    # Phase 2.8 — try the canonical (lowercased) form first since the
    # email PK is case-sensitive. If the submission is actually a
    # username the lowercase is a no-op (usernames are constrained to
    # `[a-zA-Z0-9]+` so the form is unchanged by `String.downcase/1`).
    canonical = canonical_email(email_or_username)

    # Try email first (faster with primary key)
    user = Repo.get(User, canonical)

    # If not found by email, try username (uses index) — using the
    # caller's original casing since the username column is also
    # case-sensitive and we don't lowercase usernames at register.
    user =
      if is_nil(user) do
        Repo.one(from(u in User, where: u.username == ^email_or_username))
      else
        user
      end

    cond do
      is_nil(user) ->
        {:error, :not_found}

      not user.verified ->
        {:error, :not_verified}

      not Bcrypt.verify_pass(password, user.password_hash) ->
        {:error, :wrong_password}

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

  # ──────────────────────────── Logout ───────────────────────────────────────────
  #
  # Phase 2.4 — Logout now FLAGS the session as revoked (revoked_at) rather
  # than DELETE-ing it so we have a server-side revocation list / audit
  # trail. The token stops resolving to a user immediately, well before
  # the natural 7d expiry, and `cleanup_expired_sessions` later reaps rows
  # that are both past `expired_at`. This is the same mechanism an admin
  # force-off would use (`revoke_token/1`).

  def logout(token) do
    case Repo.get(Session, token) do
      nil ->
        {:error, :invalid_token}

      session ->
        if is_nil(session.revoked_at) do
          now = DateTime.utc_now() |> DateTime.truncate(:second)

          session
          |> Ecto.Changeset.change(%{revoked_at: now})
          |> Repo.update!()
        end

        {:ok, :logged_out}
    end
  end

  # Phase 2.4 — explicit revocation list entry. Idempotent: revoking an
  # already-revoked token is a no-op. Used by security-event handlers
  # (e.g. WS disconnect on logout) so a stolen-but-still-cached token is
  # rejected on the next lookup.
  def revoke_token(token) when is_binary(token) do
    case Repo.get(Session, token) do
      nil ->
        {:error, :invalid_token}

      session ->
        if is_nil(session.revoked_at) do
          now = DateTime.utc_now() |> DateTime.truncate(:second)

          session
          |> Ecto.Changeset.change(%{revoked_at: now})
          |> Repo.update!()
        end

        {:ok, :revoked}
    end
  end

  def revoke_token(_), do: {:error, :invalid_token}

  # ──────────────────────────── Get User by Token ───────────────────────────────

  def get_user_by_token(token) do
    case Repo.get(Session, token) do
      nil ->
        {:error, :not_found}

      session ->
        # Phase 2.4 — revoked tokens are dead even when not yet expired.
        now = DateTime.utc_now()

        revoked? = not is_nil(session.revoked_at)

        cond do
          revoked? ->
            {:error, :revoked}

          DateTime.compare(now, session.expired_at) != :lt ->
            # Session expired, delete it
            Repo.delete(session)
            {:error, :expired}

          true ->
            case Repo.get(User, session.email) do
              nil -> {:error, :not_found}
              user -> {:ok, user}
            end
        end
    end
  end

  def get_email_by_token(token) do
    now = DateTime.utc_now()

    # Phase 2.4 — revoked row no longer resolves to a user (left join
    # via `revoked_at IS NULL`). Combining the revoked and expired
    # predicates in this one query keeps service-to-service callers
    # (gateway, signaling, matchmaking) from needing a second helper
    # round-trip.
    Repo.one(
      from(s in Session,
        where:
          s.token == ^token and is_nil(s.revoked_at) and
            s.expired_at > ^now,
        select: s.email
      )
    )
  end

  # ──────────────────────────── Session Cleanup ─────────────────────────────────

  def cleanup_expired_sessions do
    # Phase 2.4 — also reap rows that have been revoked (revoked_at is
    # set) AND are past their natural expiry. Revoked-but-not-yet-expired
    # rows are kept so the revocation list keeps refusing repeat lookups
    # until expired_at; once both are true it's safe to delete.
    now = DateTime.utc_now()

    {count, _} =
      Repo.delete_all(
        from(s in Session,
          where: s.expired_at <= ^now and not is_nil(s.revoked_at)
        )
      )

    # Expired-but-not-revoked rows: also clean up (old behavior).
    {count2, _} = Repo.delete_all(from(s in Session, where: s.expired_at <= ^now))

    {:ok, count + count2}
  end

  # ──────────────────────────── Helper Functions ────────────────────────────────────

  defp user_exists?(email) do
    Repo.get(User, email) != nil
  end

  defp username_taken?(username) do
    Repo.get_by(User, username: username) != nil
  end

  defp generate_otp do
    # Phase 0.8 / 2.1 — crypto PRNG. :rand.uniform is a Mersenne Twister
    # (predictable from a few outputs); :crypto.strong_rand_bytes uses the
    # OS CSPRNG. 3 bytes → 24 bits → 16.7M values, more than enough for a
    # 6-digit code and removes the previous bias.
    :crypto.strong_rand_bytes(3)
    |> :binary.decode_unsigned()
    |> rem(1_000_000)
    |> Integer.to_string()
    |> String.pad_leading(6, "0")
  end

  defp generate_token, do: :crypto.strong_rand_bytes(32) |> Base.url_encode64(padding: false)
end
