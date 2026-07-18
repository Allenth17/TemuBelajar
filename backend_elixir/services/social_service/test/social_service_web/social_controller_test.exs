defmodule SocialServiceWeb.SocialControllerTest do
  use SocialServiceWeb.ConnCase, async: true

  alias SocialService.Social

  # ── 5.41 — verify the FriendRequests entry route exists and is reachable ──

  describe "GET /api/social/friend-requests/pending (5.41)" do
    test "route exists and returns 200 with the requests list shape" do
      conn =
        build_conn()
        |> internal_conn("alice@ui.ac.id")
        |> get("/api/social/friend-requests/pending")

      assert response(conn, 200)
      body = json_response(conn, 200)
      assert Map.has_key?(body, "requests")
      assert is_list(body["requests"])
    end

    test "rejects unauthenticated (no internal secret) callers" do
      conn = build_conn() |> get("/api/social/friend-requests/pending")
      assert response(conn, 401)
    end
  end

  # ── 5.31 — block-list fetch endpoint used by matchmaking_service ─────────

  describe "GET /api/internal/blocked-by/:email (5.31)" do
    test "route exists; returns the blocked-set shape for the given user" do
      # Seed a block: alice blocked carol
      :ok = Social.block("alice@ui.ac.id", "carol@ui.ac.id")

      conn =
        build_conn()
        |> internal_conn("alice@ui.ac.id")
        |> get("/api/internal/blocked-by/alice@ui.ac.id")

      assert response(conn, 200)
      body = json_response(conn, 200)
      assert body["email"] == "alice@ui.ac.id"
      assert "carol@ui.ac.id" in body["blocked"]
    end

    test "returns both directions — users who blocked the caller are listed too" do
      # carol blocked alice (alice is being asked about)
      :ok = Social.block("carol@ui.ac.id", "alice@ui.ac.id")

      conn =
        build_conn()
        |> internal_conn("alice@ui.ac.id")
        |> get("/api/internal/blocked-by/alice@ui.ac.id")

      body = json_response(conn, 200)
      # alice appears as the blocked-by side: carol should be in the set
      assert "carol@ui.ac.id" in body["blocked"]
    end

    test "returns empty list when the user has no blocks" do
      conn =
        build_conn()
        |> internal_conn("nobody@ui.ac.id")
        |> get("/api/internal/blocked-by/nobody@ui.ac.id")

      body = json_response(conn, 200)
      assert body["blocked"] == []
    end

    test "rejects unauthenticated callers" do
      conn = build_conn() |> get("/api/internal/blocked-by/alice@ui.ac.id")
      assert response(conn, 401)
    end
  end
end
