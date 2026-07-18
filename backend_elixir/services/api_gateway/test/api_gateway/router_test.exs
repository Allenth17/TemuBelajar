defmodule ApiGateway.RouterTest do
  @moduledoc """
  Phase 8.10 — tests for the API Gateway router, asserting *specific* upstream
  responses rather than the loose `[200,201,400,401,404,422,503]`

  swallow-everything set the previous version used (which meant the test
  reported green whether the upstream returned 200, was down with 503, or
  was missing the route entirely with 404).

  Strategy:
    * The controller now calls `ApiGateway.HTTPClient.request/5` (a thin
      behaviour) and `ApiGateway.AuthVerifier.resolve_email/1`. Under
      :test the conn_case swaps both for Mox mocks, so we can stub exact
      responses without booting auth_service / user_service / social_service.
    * Each test stubs `resolve_email/1` (for auth-required routes) and
      `HTTPClient.request/5` (for the proxied step), then asserts on the
      exact status the gateway forwards back and on selected body keys.
  """

  use ApiGatewayWeb.ConnCase, async: true

  import Mox

  alias ApiGateway.{HTTPClientMock, AuthVerifierMock}

  # ─── Mox helpers ──────────────────────────────────────────────────────────

  # Proxies upstream responses as the live `do_request/5` unwraps them.
  # `HTTPoison.Response` / `HTTPoison.Error` struct shapes the Mox mock has
  # to match by returning built structs.
  defp upstream_response(status, body) when is_integer(status) and is_map(body) do
    {:ok, %HTTPoison.Response{status_code: status, body: Jason.encode!(body)}}
  end

  defp upstream_error(reason) do
    {:error, %HTTPoison.Error{reason: reason}}
  end

  defp stub_proxy(resp) do
    expect(HTTPClientMock, :request, fn _method, _url, _body, _h, _opts -> resp end)
  end

  defp stub_bearer(email \\ "test@ui.ac.id") do
    expect(AuthVerifierMock, :resolve_email, fn _token -> {:ok, email} end)
  end

  # Auth routes are public — they only need the HTTPClient stub (no bearer
  # resolver). Authenticated routes additionally stub resolve_email/1.

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

  # ── Auth routes → auth_service ─────────────────────────────────────────────

  describe "POST /api/register" do
    test "forwards upstream 201 back to the client", %{conn: conn} do
      stub_proxy(upstream_response(201, %{"email" => "test@ui.ac.id"}))

      conn =
        post(conn, "/api/register", %{
          "email" => "test@ui.ac.id",
          "username" => "testuser",
          "name" => "Test User",
          "password" => "Password123!"
        })

      assert conn.status == 201
      assert Jason.decode!(conn.resp_body)["email"] == "test@ui.ac.id"
    end

    test "forwards upstream 422 when params are invalid", %{conn: conn} do
      stub_proxy(upstream_response(422, %{"errors" => %{"email" => ["already taken"]}}))

      conn = post(conn, "/api/register", %{})
      assert conn.status == 422
      assert Jason.decode!(conn.resp_body)["errors"]["email"] == ["already taken"]
    end
  end

  describe "POST /api/verify-otp" do
    test "forwards upstream 200 with the verified email", %{conn: conn} do
      stub_proxy(upstream_response(200, %{"email" => "test@ui.ac.id"}))

      conn = post(conn, "/api/verify-otp", %{"email" => "test@ui.ac.id", "otp" => "123456"})
      assert conn.status == 200
      assert Jason.decode!(conn.resp_body)["email"] == "test@ui.ac.id"
    end
  end

  describe "POST /api/resend-otp" do
    test "forwards upstream 202 (accepted for retry)", %{conn: conn} do
      stub_proxy(upstream_response(202, %{"status" => "sent"}))

      conn = post(conn, "/api/resend-otp", %{"email" => "test@ui.ac.id"})
      assert conn.status == 202
      assert Jason.decode!(conn.resp_body)["status"] == "sent"
    end
  end

  describe "POST /api/login" do
    test "forwards upstream 200 with token", %{conn: conn} do
      stub_proxy(upstream_response(200, %{"token" => "abc"}))

      conn = post(conn, "/api/login", %{"email" => "test@ui.ac.id", "password" => "pass"})
      assert conn.status == 200
      assert Jason.decode!(conn.resp_body)["token"] == "abc"
    end
  end

  describe "GET /api/me" do
    test "forwards upstream 200 with the resolved user when Bearer resolves upstream-side", %{
      conn: conn
    } do
      # /api/me is a *public* proxy — the gateway itself doesn't resolve the
      # bearer; auth_service does and returns 200 + user or 401.
      stub_proxy(upstream_response(200, %{"email" => "test@ui.ac.id", "username" => "testuser"}))

      conn =
        conn
        |> put_req_header("authorization", "Bearer test_token")
        |> get("/api/me")

      assert conn.status == 200
      body = Jason.decode!(conn.resp_body)
      assert body["email"] == "test@ui.ac.id"
      assert body["username"] == "testuser"
    end

    test "forwards upstream 401 from auth_service when no Bearer", %{conn: conn} do
      stub_proxy(upstream_response(401, %{"error" => "Not authenticated"}))

      conn = get(conn, "/api/me")
      assert conn.status == 401
      assert Jason.decode!(conn.resp_body)["error"] == "Not authenticated"
    end
  end

  describe "POST /api/logout" do
    test "forwards upstream 200 logout confirmation", %{conn: conn} do
      stub_proxy(upstream_response(200, %{"status" => "logged_out"}))

      conn =
        conn
        |> put_req_header("authorization", "Bearer test_token")
        |> post("/api/logout", %{})

      assert conn.status == 200
      assert Jason.decode!(conn.resp_body)["status"] == "logged_out"
    end
  end

  describe "DELETE /api/expired-sessions" do
    test "forwards upstream 204 cleanup confirmation", %{conn: conn} do
      stub_proxy(upstream_response(204, %{}))

      conn = delete(conn, "/api/expired-sessions")
      assert conn.status == 204
    end
  end

  # ── User routes → user_service (authenticated) ────────────────────────────

  describe "GET /api/users" do
    test "returns upstream 200 + paginated users when bearer resolves", %{conn: conn} do
      stub_bearer()
      stub_proxy(upstream_response(200, %{"users" => [%{"email" => "test@ui.ac.id"}]}))

      conn =
        conn
        |> put_req_header("authorization", "Bearer test_token")
        |> get("/api/users")

      assert conn.status == 200
      body = Jason.decode!(conn.resp_body)
      assert length(body["users"]) == 1
      assert hd(body["users"])["email"] == "test@ui.ac.id"
    end

    test "returns 401 from the gateway when no bearer", %{conn: conn} do
      conn = get(conn, "/api/users")
      assert conn.status == 401
    end
  end

  describe "GET /api/users/search" do
    test "forwards upstream 200 search results with q param", %{conn: conn} do
      stub_bearer()
      stub_proxy(upstream_response(200, %{"results" => ["budi@s.ui.ac.id"]}))

      conn =
        conn
        |> put_req_header("authorization", "Bearer test_token")
        |> get("/api/users/search?q=budi")

      assert conn.status == 200
      assert Jason.decode!(conn.resp_body)["results"] == ["budi@s.ui.ac.id"]
    end

    test "forwards upstream 400 when q is missing upstream-side", %{conn: conn} do
      stub_bearer()
      stub_proxy(upstream_response(400, %{"error" => "q parameter required"}))

      conn =
        conn
        |> put_req_header("authorization", "Bearer test_token")
        |> get("/api/users/search")

      assert conn.status == 400
      assert Jason.decode!(conn.resp_body)["error"] == "q parameter required"
    end
  end

  describe "GET /api/user/:email" do
    test "forwards upstream 200 for an existing email", %{conn: conn} do
      stub_bearer()
      stub_proxy(upstream_response(200, %{"email" => "test@ui.ac.id", "name" => "Test"}))

      conn =
        conn
        |> put_req_header("authorization", "Bearer test_token")
        |> get("/api/user/test@ui.ac.id")

      assert conn.status == 200
      assert Jason.decode!(conn.resp_body)["name"] == "Test"
    end

    test "forwards upstream 404 for an unknown email", %{conn: conn} do
      stub_bearer()
      stub_proxy(upstream_response(404, %{"error" => "user not found"}))

      conn =
        conn
        |> put_req_header("authorization", "Bearer test_token")
        |> get("/api/user/missing@ui.ac.id")

      assert conn.status == 404
      assert Jason.decode!(conn.resp_body)["error"] == "user not found"
    end
  end

  describe "PUT /api/user/:email" do
    test "forwards upstream 200 with updated profile", %{conn: conn} do
      stub_bearer()
      stub_proxy(upstream_response(200, %{"name" => "Updated Name"}))

      conn =
        conn
        |> put_req_header("authorization", "Bearer test_token")
        |> put("/api/user/test@ui.ac.id", %{"name" => "Updated Name"})

      assert conn.status == 200
      assert Jason.decode!(conn.resp_body)["name"] == "Updated Name"
    end
  end

  # ── Matchmaking routes (authenticated) ───────────────────────────────────

  describe "POST /api/matchmaking/join" do
    test "forwards 200 + queued confirmation", %{conn: conn} do
      stub_bearer()
      stub_proxy(upstream_response(200, %{"status" => "queued", "email" => "test@ui.ac.id"}))

      conn =
        conn
        |> put_req_header("authorization", "Bearer test_token")
        |> post("/api/matchmaking/join", %{"university" => "UI", "major" => "informatika"})

      assert conn.status == 200
      body = Jason.decode!(conn.resp_body)
      assert body["status"] == "queued"
      # Gateway should overwrite body email with the verified caller email.
      assert body["email"] == "test@ui.ac.id"
    end

    test "returns 401 when no bearer", %{conn: conn} do
      conn = post(conn, "/api/matchmaking/join", %{})
      assert conn.status == 401
    end
  end

  describe "POST /api/matchmaking/leave" do
    test "forwards 204 when leaving", %{conn: conn} do
      stub_bearer()
      stub_proxy(upstream_response(204, %{}))

      conn =
        conn
        |> put_req_header("authorization", "Bearer test_token")
        |> post("/api/matchmaking/leave", %{})

      assert conn.status == 204
    end
  end

  # ── Social routes (authenticated via X-Caller-Email injected from Bearer) ─

  describe "POST /api/social/follow" do
    test "forwards upstream 201 on new follow", %{conn: conn} do
      # `proxy_social` uses AuthBridge.internal_headers → also goes through
      # resolve_email. Stub bearer → email for it.
      stub_bearer()
      stub_proxy(upstream_response(201, %{"followed" => "alice@ui.ac.id"}))

      conn =
        conn
        |> put_req_header("authorization", "Bearer test_token")
        |> post("/api/social/follow", %{"target" => "alice@ui.ac.id"})

      assert conn.status == 201
      assert Jason.decode!(conn.resp_body)["followed"] == "alice@ui.ac.id"
    end

    test "returns 401 from gateway when no bearer", %{conn: conn} do
      conn = post(conn, "/api/social/follow", %{"target" => "alice@ui.ac.id"})
      assert conn.status == 401
    end
  end

  describe "GET /api/social/profile/:email" do
    test "forwards upstream 200 with the profile", %{conn: conn} do
      stub_bearer()

      stub_proxy(
        upstream_response(200, %{
          "follower_count" => 3,
          "following_count" => 5,
          "email" => "test@ui.ac.id"
        })
      )

      conn =
        conn
        |> put_req_header("authorization", "Bearer test_token")
        |> get("/api/social/profile/test@ui.ac.id")

      assert conn.status == 200
      body = Jason.decode!(conn.resp_body)
      assert body["follower_count"] == 3
      assert body["following_count"] == 5
    end
  end

  # ── Service unreachable: explicit 503 (Phase 8.10 fix) ──────────────────────

  describe "downstream service unavailable" do
    test "POST /api/login surfaces a 503 with error key when HTTP errors", %{conn: conn} do
      stub_proxy(upstream_error(:econnrefused))

      conn = post(conn, "/api/login", %{"email" => "test@ui.ac.id", "password" => "pass"})
      assert conn.status == 503
      body = Jason.decode!(conn.resp_body)
      # The gateway tags upstream-down responses with `error: "Service unavailable"`,
      # distinguishing them from a legitimate 503 the upstream might forward.
      assert body["error"] == "Service unavailable"
      assert body["reason"] == "econnrefused"
    end
  end

  # ── Unknown routes ─────────────────────────────────────────────────────────

  describe "unknown routes" do
    test "returns 404 for unregistered path", %{conn: conn} do
      conn = get(conn, "/api/nonexistent_route_xyz")
      assert conn.status == 404
    end
  end
end
