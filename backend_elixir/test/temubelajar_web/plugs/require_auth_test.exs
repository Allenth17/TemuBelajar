defmodule TemuBelajarWeb.Plugs.RequireAuthTest do
  use TemuBelajarWeb.ConnCase, async: true
  alias TemuBelajarWeb.Plugs.RequireAuth
  alias TemuBelajar.Accounts

  @valid_attrs %{
    "email" => "stud@ui.ac.id",
    "name" => "Stud",
    "username" => "stud",
    "phone" => "08",
    "university" => "UI",
    "password" => "secret123"
  }

  setup do
    Accounts.register_user(@valid_attrs)
    user = TemuBelajar.Repo.get(Accounts.User, @valid_attrs["email"])
    Accounts.verify_otp(user.email, user.otp)
    {:ok, token} = Accounts.login(user.email, "secret123")
    %{token: token, user: user}
  end

  test "halts and returns 401 when missing token", %{conn: conn} do
    conn = RequireAuth.call(conn, RequireAuth.init([]))
    assert conn.halted
    assert conn.status == 401
  end

  test "halts and returns 401 when invalid token", %{conn: conn} do
    conn = 
      conn 
      |> put_req_header("authorization", "Bearer BADTOKEN")
      |> RequireAuth.call(RequireAuth.init([]))
    
    assert conn.halted
    assert conn.status == 401
  end

  test "assigns current_email when valid token", %{conn: conn, token: token, user: user} do
    conn = 
      conn 
      |> put_req_header("authorization", "Bearer #{token}")
      |> RequireAuth.call(RequireAuth.init([]))
    
    refute conn.halted
    assert conn.assigns.current_email == user.email
  end
end
