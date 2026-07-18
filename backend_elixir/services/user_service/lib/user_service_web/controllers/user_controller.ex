defmodule UserServiceWeb.UserController do
  use UserServiceWeb, :controller

  alias UserService.Users

  @admin_emails_env "ADMIN_EMAILS"

  # GET /api/user/:email
  # Any authenticated caller may read a profile (it is the social-graph
  # lookup). The gateway ensures caller_email is set; list_users below
  # is the actual enumeration oracle and is admin-gated.
  def get_user(conn, %{"email" => email}) do
    case Users.get_user(email) do
      nil ->
        conn |> put_status(404) |> json(%{error: "User tidak ditemukan"})

      user ->
        json(conn, profile_json(user))
    end
  end

  # PUT /api/user/:email
  # Only the owner can edit their own profile (Phase 0.4). Major/bio/
  # avatar_url are now accepted (Phase 5.35 / 5.42).
  def update_user(conn, %{"email" => email} = params) do
    caller = conn.assigns[:caller_email]

    if caller != email do
      conn |> put_status(403) |> json(%{error: "Cannot edit another user's profile"})
    else
      attrs =
        Map.take(params, ["name", "username", "phone", "university", "major", "bio", "avatar_url"])

      case Users.update_user(email, attrs) do
        {:ok, user} ->
          json(conn, %{message: "Profile berhasil diupdate", user: profile_json(user)})

        {:error, :not_found} ->
          conn |> put_status(404) |> json(%{error: "User tidak ditemukan"})

        {:error, changeset} ->
          errors =
            Ecto.Changeset.traverse_errors(changeset, fn {msg, opts} ->
              Enum.reduce(opts, msg, fn {key, value}, acc ->
                String.replace(acc, "%{#{key}}", to_string(value))
              end)
            end)

          conn |> put_status(400) |> json(%{error: "Validation failed", details: errors})
      end
    end
  end

  # GET /api/users — admin-only enumeration (Phase 0.4).
  def list_users(conn, params) do
    with :ok <- require_admin(conn) do
      limit = parse_int(params["limit"], 100)
      users = Users.list_users(limit)

      json(conn, %{
        users: Enum.map(users, &brief_json/1),
        count: length(users)
      })
    end
  end

  # GET /api/users/search — admin-only.
  def search_users(conn, %{"q" => query} = params) do
    with :ok <- require_admin(conn) do
      limit = parse_int(params["limit"], 20)
      users = Users.search_users(query, limit)

      json(conn, %{
        users: Enum.map(users, &brief_json/1),
        count: length(users)
      })
    end
  end

  def search_users(conn, _params) do
    conn |> put_status(400) |> json(%{error: "Query parameter `q` is required"})
  end

  # ─── Helpers ───────────────────────────────────────────────────────────────

  defp parse_int(nil, default), do: default

  defp parse_int(v, default) when is_binary(v) do
    case Integer.parse(v) do
      {n, _} -> n
      :error -> default
    end
  end

  defp parse_int(v, default) when is_integer(v), do: v
  defp parse_int(_, default), do: default

  defp require_admin(conn) do
    caller = conn.assigns[:caller_email]

    admins =
      @admin_emails_env
      |> System.get_env("")
      |> String.split(",", trim: true)
      |> Enum.map(&String.trim/1)

    if caller in admins do
      :ok
    else
      conn
      |> put_status(403)
      |> json(%{error: "Admin access required"})
      |> halt()
    end
  end

  # Safe JSON for full profile (no password_hash / otp / verified — those
  # belong to auth_service; see Phase 0.10).
  defp profile_json(user) do
    %{
      email: user.email,
      name: user.name,
      username: user.username,
      phone: user.phone,
      university: user.university,
      major: user.major,
      bio: user.bio,
      avatar_url: user.avatar_url,
      last_login: user.last_login
    }
  end

  defp brief_json(user) do
    %{email: user.email, name: user.name, username: user.username}
  end
end
