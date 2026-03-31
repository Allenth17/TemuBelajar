defmodule UserServiceWeb.UserControllerTest do
  use UserServiceWeb.ConnCase

  setup do
    user = %UserService.Users.User{
      email: "test@ui.ac.id",
      name: "Test User",
      username: "testuser",
      university: "Universitas Indonesia",
      password_hash: "dummy_hash"
    } |> UserService.Repo.insert!()
    {:ok, user: user}
  end

  describe "GET /api/user/:email" do
    test "returns user profile for authenticated user", %{conn: conn} do
      conn = get(conn, "/api/user/test@ui.ac.id")
      assert response(conn, 200)
    end
  end

  describe "PUT /api/user/:email" do
    test "updates user profile for authenticated user", %{conn: conn} do
      conn =
        put(conn, "/api/user/test@ui.ac.id", %{
          "name" => "Updated Name",
          "phone" => "1234567890"
        })

      assert response(conn, 200)
    end
  end
end
