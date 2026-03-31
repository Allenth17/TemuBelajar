defmodule UserServiceWeb.UserController do
  use UserServiceWeb, :controller

  alias UserService.Users

  # GET /api/user/:email
  def get_user(conn, %{"email" => email}) do
    case Users.get_user(email) do
      nil ->
        conn |> put_status(404) |> json(%{error: "User tidak ditemukan"})

      user ->
        json(conn, %{
          email: user.email,
          name: user.name,
          username: user.username,
          phone: user.phone,
          university: user.university,
          verified: user.verified,
          last_login: user.last_login
        })
    end
  end

  # PUT /api/user/:email
  def update_user(conn, %{"email" => email} = params) do
    attrs = Map.take(params, ["name", "username", "phone", "university"])

    case Users.update_user(email, attrs) do
      {:ok, user} ->
        json(conn, %{
          message: "Profile berhasil diupdate",
          user: %{
            email: user.email,
            name: user.name,
            username: user.username,
            phone: user.phone,
            university: user.university
          }
        })

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

  # GET /api/users
  def list_users(conn, params) do
    limit = Map.get(params, "limit", "100") |> String.to_integer()
    users = Users.list_users(limit)

    json(conn, %{
      users:
        Enum.map(users, fn user ->
          %{
            email: user.email,
            name: user.name,
            username: user.username,
            verified: user.verified
          }
        end),
      count: length(users)
    })
  end

  # GET /api/users/search
  def search_users(conn, %{"q" => query} = params) do
    limit = Map.get(params, "limit", "20") |> String.to_integer()
    users = Users.search_users(query, limit)

    json(conn, %{
      users:
        Enum.map(users, fn user ->
          %{
            email: user.email,
            name: user.name,
            username: user.username,
            verified: user.verified
          }
        end),
      count: length(users)
    })
  end
end
