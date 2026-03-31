defmodule MatchmakingServiceWeb.MatchmakingControllerTest do
  @moduledoc """
  HTTP endpoint tests for MatchmakingController.
  Uses ConnCase (no database — matchmaking is purely ETS-based).
  """

  use MatchmakingServiceWeb.ConnCase, async: false

  alias MatchmakingService.MatchmakingServer

  # ── POST /api/matchmaking/join ───────────────────────────────────────────────

  describe "POST /api/matchmaking/join" do
    test "first user is queued with a position", %{conn: conn} do
      conn =
        post(conn, "/api/matchmaking/join", %{
          "email" => "alice@ui.ac.id",
          "university" => "UI"
        })

      assert %{"status" => "queued", "position" => pos} = json_response(conn, 200)
      assert is_integer(pos) and pos >= 1
    end

    test "second user from different university is matched immediately", %{conn: conn} do
      # First user joins
      post(conn, "/api/matchmaking/join", %{
        "email" => "alice@ui.ac.id",
        "university" => "UI"
      })

      # Second user joins with a fresh conn
      conn2 = Phoenix.ConnTest.build_conn()

      conn2 =
        post(conn2, "/api/matchmaking/join", %{
          "email" => "bob@itb.ac.id",
          "university" => "ITB"
        })

      body = json_response(conn2, 200)
      assert body["status"] == "matched"
      assert is_binary(body["pair_id"]) and byte_size(body["pair_id"]) > 0
      assert body["peer_email"] == "alice@ui.ac.id"
    end

    test "second user from same university is still matched", %{conn: conn} do
      post(conn, "/api/matchmaking/join", %{
        "email" => "alice@ui.ac.id",
        "university" => "UI"
      })

      conn2 = Phoenix.ConnTest.build_conn()

      conn2 =
        post(conn2, "/api/matchmaking/join", %{
          "email" => "bob@ui.ac.id",
          "university" => "UI"
        })

      body = json_response(conn2, 200)
      assert body["status"] == "matched"
      assert body["peer_email"] == "alice@ui.ac.id"
    end

    test "joining without university field still works", %{conn: conn} do
      conn =
        post(conn, "/api/matchmaking/join", %{
          "email" => "alice@ui.ac.id",
          "university" => "UI"
        })

      assert %{"status" => status} = json_response(conn, 200)
      assert status in ["queued", "matched"]
    end

    test "already-queued user returns queued status again", %{conn: conn} do
      post(conn, "/api/matchmaking/join", %{
        "email" => "alice@ui.ac.id",
        "university" => "UI"
      })

      conn2 = Phoenix.ConnTest.build_conn()

      conn2 =
        post(conn2, "/api/matchmaking/join", %{
          "email" => "alice@ui.ac.id",
          "university" => "UI"
        })

      assert %{"status" => "queued"} = json_response(conn2, 200)
    end

    test "queue grows with multiple unmatched users", %{conn: conn} do
      post(conn, "/api/matchmaking/join", %{
        "email" => "u1@ui.ac.id",
        "university" => "UI"
      })

      post(Phoenix.ConnTest.build_conn(), "/api/matchmaking/join", %{
        "email" => "u2@ui.ac.id",
        "university" => "UI"
      })

      post(Phoenix.ConnTest.build_conn(), "/api/matchmaking/join", %{
        "email" => "u3@ui.ac.id",
        "university" => "UI"
      })

      assert MatchmakingServer.queue_size() == 3
    end

    test "matched response includes pair_id and peer_email", %{conn: conn} do
      post(conn, "/api/matchmaking/join", %{
        "email" => "alice@ui.ac.id",
        "university" => "UI"
      })

      conn2 = Phoenix.ConnTest.build_conn()

      conn2 =
        post(conn2, "/api/matchmaking/join", %{
          "email" => "bob@itb.ac.id",
          "university" => "ITB"
        })

      body = json_response(conn2, 200)
      assert Map.has_key?(body, "pair_id")
      assert Map.has_key?(body, "peer_email")
      assert Map.has_key?(body, "status")
    end

    test "cross-university match is chosen over same-university when both available", %{conn: conn} do
      # Add a same-uni candidate
      post(conn, "/api/matchmaking/join", %{
        "email" => "same@ui.ac.id",
        "university" => "UI"
      })

      # Add a cross-uni candidate
      post(Phoenix.ConnTest.build_conn(), "/api/matchmaking/join", %{
        "email" => "cross@itb.ac.id",
        "university" => "ITB"
      })

      # New UI user joins — should be matched with cross-uni (higher score)
      conn3 = Phoenix.ConnTest.build_conn()

      conn3 =
        post(conn3, "/api/matchmaking/join", %{
          "email" => "new@ui.ac.id",
          "university" => "UI"
        })

      body = json_response(conn3, 200)
      assert body["status"] == "matched"
      assert body["peer_email"] == "cross@itb.ac.id"
    end
  end

  # ── POST /api/matchmaking/leave ──────────────────────────────────────────────

  describe "POST /api/matchmaking/leave" do
    test "user can leave the queue", %{conn: conn} do
      post(conn, "/api/matchmaking/join", %{
        "email" => "alice@ui.ac.id",
        "university" => "UI"
      })

      assert MatchmakingServer.queue_size() == 1

      conn2 = Phoenix.ConnTest.build_conn()

      conn2 =
        post(conn2, "/api/matchmaking/leave", %{
          "email" => "alice@ui.ac.id"
        })

      assert %{"status" => "left"} = json_response(conn2, 200)
      assert MatchmakingServer.queue_size() == 0
    end

    test "leaving a user not in queue returns ok (idempotent)", %{conn: conn} do
      conn =
        post(conn, "/api/matchmaking/leave", %{
          "email" => "ghost@ui.ac.id"
        })

      assert %{"status" => "left"} = json_response(conn, 200)
    end

    test "after leaving, another user won't match with the left user", %{conn: conn} do
      post(conn, "/api/matchmaking/join", %{
        "email" => "alice@ui.ac.id",
        "university" => "UI"
      })

      post(Phoenix.ConnTest.build_conn(), "/api/matchmaking/leave", %{
        "email" => "alice@ui.ac.id"
      })

      conn3 = Phoenix.ConnTest.build_conn()

      conn3 =
        post(conn3, "/api/matchmaking/join", %{
          "email" => "bob@itb.ac.id",
          "university" => "ITB"
        })

      assert %{"status" => "queued"} = json_response(conn3, 200)
    end
  end

  # ── POST /api/matchmaking/end-pair ───────────────────────────────────────────

  describe "POST /api/matchmaking/end-pair" do
    test "ending an active pair returns ok", %{conn: conn} do
      post(conn, "/api/matchmaking/join", %{
        "email" => "alice@ui.ac.id",
        "university" => "UI"
      })

      conn2 = Phoenix.ConnTest.build_conn()

      conn2 =
        post(conn2, "/api/matchmaking/join", %{
          "email" => "bob@itb.ac.id",
          "university" => "ITB"
        })

      %{"pair_id" => pair_id} = json_response(conn2, 200)

      conn3 = Phoenix.ConnTest.build_conn()

      conn3 =
        post(conn3, "/api/matchmaking/end-pair", %{
          "pair_id" => pair_id
        })

      assert %{"status" => "ok"} = json_response(conn3, 200)
    end

    test "ending a non-existent pair is a no-op and returns ok", %{conn: conn} do
      conn =
        post(conn, "/api/matchmaking/end-pair", %{
          "pair_id" => "nonexistent-pair"
        })

      assert %{"status" => "ok"} = json_response(conn, 200)
    end
  end

  # ── GET /api/health ──────────────────────────────────────────────────────────

  describe "GET /api/health" do
    test "returns 200 with service name", %{conn: conn} do
      conn = get(conn, "/api/health")
      body = json_response(conn, 200)
      assert body["status"] == "ok"
      assert body["service"] == "matchmaking_service"
    end
  end
end
