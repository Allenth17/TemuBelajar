defmodule AuthServiceWeb.ConnCase do
  use ExUnit.CaseTemplate

  using do
    quote do
      import Phoenix.ConnTest
      import Plug.Conn
      import AuthServiceWeb.ConnCase

      @endpoint AuthServiceWeb.Endpoint
    end
  end

  setup tags do
    pid = Ecto.Adapters.SQL.Sandbox.start_owner!(AuthService.Repo, shared: not tags[:async])
    on_exit(fn -> Ecto.Adapters.SQL.Sandbox.stop_owner(pid) end)
    {:ok, conn: Phoenix.ConnTest.build_conn()}
  end

  @doc """
  Setup helper that registers and logs in a user.

      setup :register_and_log_in_user

  It returns an `:ok` tuple with the connection and user.
  """
  def register_and_log_in_user(%{conn: conn}) do
    user = AuthService.AccountsFixtures.user_fixture()
    {:ok, token} = AuthService.Accounts.login(user.email, "password123")

    conn =
      conn
      |> Plug.Conn.put_req_header("authorization", "Bearer #{token}")
      |> Plug.Conn.put_req_header("accept", "application/json")

    {:ok, conn: conn, user: user, token: token}
  end
end
