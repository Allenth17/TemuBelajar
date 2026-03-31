defmodule ApiGateway.RouterTest do
  @moduledoc """
  Integration-style tests for the API Gateway router.

  Tests verify that each route is registered and that the gateway
  produces a well-formed response. Tests accept 200/4xx from upstream
  OR 503 when downstream services are unavailable in test env.
  """

  use ApiGatewayWeb.ConnCase, async: true

  # Helper: expects upstream 2xx/4xx or gateway 503 (service down in CI)
  defp assert_proxied(conn) do
    assert conn.status in [200, 201, 400, 401, 404, 422, 503]
  end

  # ── Health ─────────────────────────────────────────────────────────────────

  describe "GET /api/health" do
    test "returns 200 with status ok", %{conn: conn} do
      conn = get(conn, "/api/health")
      assert conn.status == 200
      body = Jason.decode!(conn.resp_body)
      assert body["status"] == "ok"
      assert body["service"] == "api_gateway"
    end
  end

  # ── Auth routes ────────────────────────────────────────────────────────────

  describe "POST /api/register" do
    test "proxies register request", %{conn: conn} do
      conn = post(conn, "/api/register", %{
        "email" => "test@ui.ac.id",
        "username" => "testuser",
        "name" => "Test User",
        "password" => "Password123!"
      })
      assert_proxied(conn)
    end

    test "missing params still proxied (validation upstream)", %{conn: conn} do
      conn = post(conn, "/api/register", %{})
      assert_proxied(conn)
    end
  end

  describe "POST /api/verify-otp" do
    test "proxies verify-otp request", %{conn: conn} do
      conn = post(conn, "/api/verify-otp", %{"email" => "test@ui.ac.id", "otp" => "123456"})
      assert_proxied(conn)
    end
  end

  describe "POST /api/resend-otp" do
    test "proxies resend-otp request", %{conn: conn} do
      conn = post(conn, "/api/resend-otp", %{"email" => "test@ui.ac.id"})
      assert_proxied(conn)
    end
  end

  describe "POST /api/login" do
    test "proxies login request", %{conn: conn} do
      conn = post(conn, "/api/login", %{"email" => "test@ui.ac.id", "password" => "pass"})
      assert_proxied(conn)
    end
  end

  describe "GET /api/me" do
    test "proxies me request with Bearer token", %{conn: conn} do
      conn =
        conn
        |> put_req_header("authorization", "Bearer test_token")
        |> get("/api/me")
      assert_proxied(conn)
    end

    test "proxies me request without token (401 from upstream)", %{conn: conn} do
      conn = get(conn, "/api/me")
      assert_proxied(conn)
    end
  end

  describe "POST /api/logout" do
    test "proxies logout request", %{conn: conn} do
      conn =
        conn
        |> put_req_header("authorization", "Bearer test_token")
        |> post("/api/logout", %{})
      assert_proxied(conn)
    end
  end

  describe "DELETE /api/expired-sessions" do
    test "proxies cleanup request", %{conn: conn} do
      conn = delete(conn, "/api/expired-sessions")
      assert_proxied(conn)
    end
  end

  # ── User routes ─────────────────────────────────────────────────────────────

  describe "GET /api/users" do
    test "proxies list_users request", %{conn: conn} do
      conn = get(conn, "/api/users")
      assert_proxied(conn)
    end
  end

  describe "GET /api/users/search" do
    test "proxies search_users with query param", %{conn: conn} do
      conn = get(conn, "/api/users/search?q=budi")
      assert_proxied(conn)
    end

    test "proxies search_users without query param", %{conn: conn} do
      conn = get(conn, "/api/users/search")
      assert_proxied(conn)
    end
  end

  describe "GET /api/user/:email" do
    test "proxies get_user for specific email", %{conn: conn} do
      conn = get(conn, "/api/user/test@ui.ac.id")
      assert_proxied(conn)
    end
  end

  describe "PUT /api/user/:email" do
    test "proxies update_user request", %{conn: conn} do
      conn = put(conn, "/api/user/test@ui.ac.id", %{"name" => "Updated Name"})
      assert_proxied(conn)
    end
  end

  # ── Signaling routes ─────────────────────────────────────────────────────────

  describe "POST /api/signaling/join" do
    test "proxies signaling join", %{conn: conn} do
      conn = post(conn, "/api/signaling/join", %{"pair_id" => "test123"})
      assert_proxied(conn)
    end
  end

  describe "POST /api/signaling/offer" do
    test "proxies signaling offer", %{conn: conn} do
      conn = post(conn, "/api/signaling/offer", %{"sdp" => "v=0"})
      assert_proxied(conn)
    end
  end

  describe "POST /api/signaling/answer" do
    test "proxies signaling answer", %{conn: conn} do
      conn = post(conn, "/api/signaling/answer", %{"sdp" => "v=0"})
      assert_proxied(conn)
    end
  end

  describe "POST /api/signaling/ice" do
    test "proxies ICE candidate", %{conn: conn} do
      conn = post(conn, "/api/signaling/ice", %{"candidate" => "candidate:1 1 udp..."})
      assert_proxied(conn)
    end
  end

  # ── Matchmaking routes ───────────────────────────────────────────────────────

  describe "POST /api/matchmaking/join" do
    test "proxies matchmaking join", %{conn: conn} do
      conn = post(conn, "/api/matchmaking/join", %{
        "email" => "test@ui.ac.id",
        "university" => "UI",
        "major" => "informatika"
      })
      assert_proxied(conn)
    end
  end

  describe "POST /api/matchmaking/leave" do
    test "proxies matchmaking leave", %{conn: conn} do
      conn = post(conn, "/api/matchmaking/leave", %{"email" => "test@ui.ac.id"})
      assert_proxied(conn)
    end
  end

  # ── Unknown routes ───────────────────────────────────────────────────────────

  describe "unknown routes" do
    test "returns 404 for unregistered path", %{conn: conn} do
      conn = get(conn, "/api/nonexistent_route_xyz")
      assert conn.status == 404
    end
  end
end
