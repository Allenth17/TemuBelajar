defmodule TemuBelajarWeb.UserSocketTest do
  use TemuBelajarWeb.ChannelCase, async: true
  alias TemuBelajarWeb.UserSocket
  alias TemuBelajar.Accounts

  @valid_attrs %{
    "email" => "socket@ui.ac.id",
    "name" => "socket",
    "username" => "socket1",
    "phone" => "123",
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

  test "connects with valid token", %{token: token, user: user} do
    assert {:ok, socket} = connect(UserSocket, %{"token" => token})
    assert socket.assigns.email == user.email
    assert socket.id == "user_socket:#{user.email}"
  end

  test "returns error with invalid token" do
    assert :error = connect(UserSocket, %{"token" => "INVALID"})
  end

  test "returns error with no token payload" do
    assert :error = connect(UserSocket, %{})
  end
end
