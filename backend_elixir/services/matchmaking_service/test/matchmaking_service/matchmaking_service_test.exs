defmodule MatchmakingService.MatchmakingServerTest do
  @moduledoc """
  Tests for the ETS-based MatchmakingServer.

  Uses async: false because the MatchmakingServer is a named singleton and
  tests share the same ETS tables. The ConnCase setup calls reset/0 before
  each test to give each test a clean slate.
  """

  use ExUnit.Case, async: false

  alias MatchmakingService.MatchmakingServer

  # ── Helpers ────────────────────────────────────────────────────────────────

  setup do
    # Ensure the server is running (it's started by the Application supervisor
    # in test mode) and reset all ETS tables before each test.
    case Process.whereis(MatchmakingServer) do
      nil ->
        {:ok, _pid} = MatchmakingServer.start_link([])

      _pid ->
        MatchmakingServer.reset()
    end

    :ok
  end

  # Advance a join timestamp backward in time by `ms` milliseconds so that
  # wait-time calculations see the user as having waited that long.
  defp past_ms(ms), do: System.monotonic_time(:millisecond) - ms

  # ── Queue join / leave ──────────────────────────────────────────────────────

  describe "join_queue/3 – single user" do
    test "first user gets :queued with position 1" do
      assert {:queued, 1} =
               MatchmakingServer.join_queue("alice@ui.ac.id", "UI", "teknik_informatika")
    end

    test "second user from different university is matched immediately" do
      MatchmakingServer.join_queue("alice@ui.ac.id", "UI", nil)
      result = MatchmakingServer.join_queue("bob@itb.ac.id", "ITB", nil)
      assert {:matched, _pair_id, "alice@ui.ac.id"} = result
    end

    test "second user from same university is still matched (same-uni is allowed, just less preferred)" do
      MatchmakingServer.join_queue("alice@ui.ac.id", "UI", nil)
      result = MatchmakingServer.join_queue("bob@ui.ac.id", "UI", nil)
      assert {:matched, _pair_id, "alice@ui.ac.id"} = result
    end

    test "pair_id is a non-empty string" do
      MatchmakingServer.join_queue("alice@ui.ac.id", "UI", nil)
      {:matched, pair_id, _} = MatchmakingServer.join_queue("bob@itb.ac.id", "ITB", nil)
      assert is_binary(pair_id) and byte_size(pair_id) > 0
    end

    test "already-queued user gets :queued with current position (idempotent)" do
      {:queued, 1} = MatchmakingServer.join_queue("alice@ui.ac.id", "UI", nil)
      assert {:queued, _} = MatchmakingServer.join_queue("alice@ui.ac.id", "UI", nil)
    end

    test "queue_size increases as users join" do
      assert MatchmakingServer.queue_size() == 0
      MatchmakingServer.join_queue("user1@ui.ac.id", "UI", nil)
      assert MatchmakingServer.queue_size() == 1
      MatchmakingServer.join_queue("user2@ui.ac.id", "UI", nil)
      assert MatchmakingServer.queue_size() == 2
    end

    test "queue_size drops to 0 after a match" do
      MatchmakingServer.join_queue("alice@ui.ac.id", "UI", nil)
      MatchmakingServer.join_queue("bob@itb.ac.id", "ITB", nil)
      assert MatchmakingServer.queue_size() == 0
    end

    test "join with nil university is accepted" do
      assert {:queued, 1} = MatchmakingServer.join_queue("alice@ui.ac.id", nil, nil)
    end

    test "three users – first two match, third stays queued" do
      MatchmakingServer.join_queue("a@ui.ac.id", "UI", nil)
      MatchmakingServer.join_queue("b@itb.ac.id", "ITB", nil)
      {:queued, pos} = MatchmakingServer.join_queue("c@ugm.ac.id", "UGM", nil)
      assert pos == 1
      assert MatchmakingServer.queue_size() == 1
    end
  end

  describe "leave_queue/1" do
    test "removes user from queue" do
      MatchmakingServer.join_queue("alice@ui.ac.id", "UI", nil)
      assert MatchmakingServer.queue_size() == 1

      MatchmakingServer.leave_queue("alice@ui.ac.id")
      assert MatchmakingServer.queue_size() == 0
    end

    test "leaving a non-existent user is a no-op" do
      assert :ok == MatchmakingServer.leave_queue("ghost@ui.ac.id")
    end

    test "after leaving, another user no longer sees a match with the left user" do
      MatchmakingServer.join_queue("alice@ui.ac.id", "UI", nil)
      MatchmakingServer.leave_queue("alice@ui.ac.id")

      assert {:queued, 1} = MatchmakingServer.join_queue("bob@itb.ac.id", "ITB", nil)
    end
  end

  describe "end_pair/1" do
    test "removes pair from active_pairs (smoke test – no crash)" do
      MatchmakingServer.join_queue("alice@ui.ac.id", "UI", nil)
      {:matched, pair_id, _} = MatchmakingServer.join_queue("bob@itb.ac.id", "ITB", nil)
      assert :ok == MatchmakingServer.end_pair(pair_id)
    end

    test "calling end_pair with unknown pair_id is a no-op" do
      assert :ok == MatchmakingServer.end_pair("nonexistent-pair-id")
    end
  end

  # ── Scoring algorithm ───────────────────────────────────────────────────────

  describe "score_match/8 – scoring algorithm" do
    test "cross-university score is higher than same-university score" do
      now = System.monotonic_time(:millisecond)

      cross_uni_score =
        MatchmakingServer.score_match(
          "a@ui.ac.id",
          "UI",
          nil,
          now,
          "b@itb.ac.id",
          "ITB",
          nil,
          now
        )

      same_uni_score =
        MatchmakingServer.score_match(
          "a@ui.ac.id",
          "UI",
          nil,
          now,
          "b2@ui.ac.id",
          "UI",
          nil,
          now
        )

      assert cross_uni_score > same_uni_score
    end

    test "score is between 0.0 and 1.0 (inclusive)" do
      now = System.monotonic_time(:millisecond)

      score =
        MatchmakingServer.score_match(
          "a@ui.ac.id",
          "UI",
          nil,
          now,
          "b@itb.ac.id",
          "ITB",
          nil,
          now
        )

      assert score >= 0.0 and score <= 1.0
    end

    test "longer wait time produces a higher score (all else equal)" do
      now = System.monotonic_time(:millisecond)
      # 30 seconds ago
      old = now - 30_000

      score_fresh =
        MatchmakingServer.score_match(
          "a@ui.ac.id",
          "UI",
          nil,
          now,
          "b@itb.ac.id",
          "ITB",
          nil,
          now
        )

      score_waited =
        MatchmakingServer.score_match(
          "a@ui.ac.id",
          "UI",
          nil,
          now,
          "b@itb.ac.id",
          "ITB",
          nil,
          old
        )

      assert score_waited > score_fresh
    end

    test "wait time score is capped at 1.0 (no score above max after 60+ seconds)" do
      now = System.monotonic_time(:millisecond)
      # 2 minutes – should be capped at 1.0
      very_long = now - 120_000

      score_60s =
        MatchmakingServer.score_match(
          "a@ui.ac.id",
          "UI",
          nil,
          now,
          "b@itb.ac.id",
          "ITB",
          nil,
          now - 60_000
        )

      score_120s =
        MatchmakingServer.score_match(
          "a@ui.ac.id",
          "UI",
          nil,
          now,
          "b@itb.ac.id",
          "ITB",
          nil,
          very_long
        )

      # Both should have the same wait contribution because it's capped
      assert_in_delta score_60s, score_120s, 0.001
    end

    test "recently matched pair gets freshness score of 0.0 (score penalty)" do
      now = System.monotonic_time(:millisecond)

      # First match them so a recent-match record is created
      MatchmakingServer.join_queue("a@ui.ac.id", "UI", nil)
      {:matched, _pair_id, _} = MatchmakingServer.join_queue("b@itb.ac.id", "ITB", nil)

      score_after_match =
        MatchmakingServer.score_match(
          "a@ui.ac.id",
          "UI",
          nil,
          now,
          "b@itb.ac.id",
          "ITB",
          nil,
          now
        )

      score_fresh_pair =
        MatchmakingServer.score_match(
          "a@ui.ac.id",
          "UI",
          nil,
          now,
          "c@ugm.ac.id",
          "UGM",
          nil,
          now
        )

      # The freshness penalty should make the recently-matched pair score lower
      assert score_fresh_pair > score_after_match
    end

    test "nil university produces a neutral (0.5) university score" do
      now = System.monotonic_time(:millisecond)

      score_nil_uni =
        MatchmakingServer.score_match(
          "a@ui.ac.id",
          nil,
          nil,
          now,
          "b@itb.ac.id",
          "ITB",
          nil,
          now
        )

      # 0.35 * 0.5 (neutral) + 0.35 * 0.0 (no wait) + 0.15 * 1.0 (fresh) + 0.15 * 0.5 (nil major) = 0.40
      assert_in_delta score_nil_uni, 0.40, 0.01
    end
  end

  # ── Major affinity scoring ────────────────────────────────────────────────────

  describe "score_match – major affinity" do
    test "same major family (both tech) scores higher than different families" do
      now = System.monotonic_time(:millisecond)

      same_family =
        MatchmakingServer.score_match(
          "a@ui.ac.id",
          "UI",
          "informatika",
          now,
          "b@itb.ac.id",
          "ITB",
          "teknik_elektro",
          now
        )

      diff_family =
        MatchmakingServer.score_match(
          "a@ui.ac.id",
          "UI",
          "informatika",
          now,
          "b@itb.ac.id",
          "ITB",
          "ekonomi",
          now
        )

      assert same_family > diff_family
    end

    test "nil major produces neutral (0.5) major score" do
      now = System.monotonic_time(:millisecond)

      score_nil =
        MatchmakingServer.score_match(
          "a@ui.ac.id",
          "UI",
          nil,
          now,
          "b@itb.ac.id",
          "ITB",
          nil,
          now
        )

      score_known =
        MatchmakingServer.score_match(
          "a@ui.ac.id",
          "UI",
          "informatika",
          now,
          "b@itb.ac.id",
          "ITB",
          "ekonomi",
          now
        )

      # nil-major is neutral (0.5 * 0.15 = 0.075 contribution)
      # known-different-family (0.2 * 0.15 = 0.03 contribution)
      assert score_nil > score_known
    end

    test "social family (ekonomi, hukum) matches correctly" do
      now = System.monotonic_time(:millisecond)

      score =
        MatchmakingServer.score_match(
          "a@ui.ac.id",
          "UI",
          "ekonomi",
          now,
          "b@itb.ac.id",
          "ITB",
          "hukum",
          now
        )

      # Same family: 0.35*1.0 + 0.35*0 + 0.15*1.0 + 0.15*0.8 = 0.35 + 0 + 0.15 + 0.12 = 0.62
      assert_in_delta score, 0.62, 0.01
    end

    test "6-arg backward-compat version still works" do
      now = System.monotonic_time(:millisecond)

      score_6 = MatchmakingServer.score_match("a@ui.ac.id", "UI", now, "b@itb.ac.id", "ITB", now)

      score_8 =
        MatchmakingServer.score_match(
          "a@ui.ac.id",
          "UI",
          nil,
          now,
          "b@itb.ac.id",
          "ITB",
          nil,
          now
        )

      assert_in_delta score_6, score_8, 0.001
    end
  end

  # ── Matchmaking preference – cross-university preferred ─────────────────────

  describe "cross-university preference in actual matching" do
    test "when both same-uni and cross-uni candidates exist, cross-uni is chosen" do
      # Two users waiting in queue
      MatchmakingServer.join_queue("same@ui.ac.id", "UI", nil)
      MatchmakingServer.join_queue("cross@itb.ac.id", "ITB", nil)

      # New user from UI joins — should be matched with the cross-uni candidate
      {:matched, _pair_id, matched_peer} = MatchmakingServer.join_queue("new@ui.ac.id", "UI", nil)

      assert matched_peer == "cross@itb.ac.id"
    end

    test "when only same-uni candidate exists, still matches (fairness)" do
      MatchmakingServer.join_queue("peer@ui.ac.id", "UI", nil)

      {:matched, _pair_id, matched_peer} = MatchmakingServer.join_queue("new@ui.ac.id", "UI", nil)

      assert matched_peer == "peer@ui.ac.id"
    end
  end

  # ── Wait-time fairness ───────────────────────────────────────────────────────

  describe "wait-time fairness – users who waited longer should be preferred" do
    test "a longer-waiting same-uni user is preferred over a freshly-joined cross-uni user" do
      # We inject entries directly into the ETS table to control timestamps.
      # The long-waiting same-uni candidate entered 55s ago.
      long_wait_ts = past_ms(55_000)
      :ets.insert(:matchmaking_queue, {"long_wait@ui.ac.id", "UI", nil, long_wait_ts})

      # A fresh cross-uni candidate just joined.
      :ets.insert(
        :matchmaking_queue,
        {"fresh@itb.ac.id", "ITB", nil, System.monotonic_time(:millisecond)}
      )

      # Score both from the perspective of a new UI user
      now = System.monotonic_time(:millisecond)

      score_long =
        MatchmakingServer.score_match(
          "new@ui.ac.id",
          "UI",
          nil,
          now,
          "long_wait@ui.ac.id",
          "UI",
          nil,
          long_wait_ts
        )

      score_fresh =
        MatchmakingServer.score_match(
          "new@ui.ac.id",
          "UI",
          nil,
          now,
          "fresh@itb.ac.id",
          "ITB",
          nil,
          now
        )

      # At 55s wait, the wait contribution overcomes the uni_score gap:
      # long_wait:  0.35*0.2 + 0.35*(55/60) + 0.15*1.0 + 0.15*0.5 = 0.07 + 0.321 + 0.15 + 0.075 = 0.616
      # fresh:      0.35*1.0 + 0.35*(0/60)  + 0.15*1.0 + 0.15*0.5 = 0.35 + 0.0   + 0.15 + 0.075 = 0.575
      assert score_long > score_fresh
    end
  end

  # ── Queue timeout ────────────────────────────────────────────────────────────

  describe "heartbeat timeout" do
    test "users who have been in queue longer than timeout are removed" do
      # Directly insert an entry with an old timestamp (>90s ago)
      expired_ts = past_ms(100_000)
      :ets.insert(:matchmaking_queue, {"expired@ui.ac.id", "UI", nil, expired_ts})

      # Also insert a fresh user
      :ets.insert(
        :matchmaking_queue,
        {"fresh@ui.ac.id", "UI", nil, System.monotonic_time(:millisecond)}
      )

      assert MatchmakingServer.queue_size() == 2

      # Trigger the heartbeat handler directly
      send(Process.whereis(MatchmakingServer), :heartbeat)

      # Give the server a moment to process the message
      :timer.sleep(50)

      # Expired user should be removed; fresh user should still be in queue
      assert MatchmakingServer.queue_size() == 1

      entries = MatchmakingServer.queue_entries()
      assert Enum.any?(entries, fn {email, _, _, _} -> email == "fresh@ui.ac.id" end)
      refute Enum.any?(entries, fn {email, _, _, _} -> email == "expired@ui.ac.id" end)
    end

    test "non-expired users are kept in queue during heartbeat" do
      # Insert a user who's been waiting 30 seconds (below 90s timeout)
      recent_ts = past_ms(30_000)
      :ets.insert(:matchmaking_queue, {"recent@ui.ac.id", "UI", nil, recent_ts})

      send(Process.whereis(MatchmakingServer), :heartbeat)
      :timer.sleep(50)

      assert MatchmakingServer.queue_size() == 1
    end
  end

  # ── Recent match TTL ─────────────────────────────────────────────────────────

  describe "recent match TTL cleanup" do
    test "stale recent-match records are purged during heartbeat" do
      # Insert a recent-match record with a timestamp older than 5 minutes
      # 6 minutes ago
      stale_ts = past_ms(360_000)
      :ets.insert(:recent_matches, {"old_key::match", stale_ts})

      # Insert a fresh recent-match record
      :ets.insert(:recent_matches, {"new_key::match", System.monotonic_time(:millisecond)})

      send(Process.whereis(MatchmakingServer), :heartbeat)
      :timer.sleep(50)

      refute :ets.member(:recent_matches, "old_key::match")
      assert :ets.member(:recent_matches, "new_key::match")
    end
  end

  # ── queue_size/0 ─────────────────────────────────────────────────────────────

  describe "queue_size/0" do
    test "returns 0 on empty queue" do
      assert MatchmakingServer.queue_size() == 0
    end

    test "accurately reflects number of waiting users" do
      MatchmakingServer.join_queue("u1@ui.ac.id", "UI", nil)
      MatchmakingServer.join_queue("u2@ui.ac.id", "UI", nil)
      MatchmakingServer.join_queue("u3@ugm.ac.id", "UGM", nil)
      assert MatchmakingServer.queue_size() == 3
    end
  end

  # ── Phase 5.31 / 5.40 — block list enforced in matchmaking ────────────────

  describe "block-list exclusion (Phase 5.31 / 5.40)" do
    @blocked_table :matchmaking_blocked_sets

    # Inject a fresh blocked-set cache entry straight into the ETS table
    # so tests don't need a running social_service. expiry is far in the
    # future so the cache lookup returns this set instead of invoking the
    # (no-backend-configured) HTTP path.
    defp seed_blocked_cache(email, blocked_emails) do
      expiry = System.monotonic_time(:millisecond) + 60_000
      :ets.insert(@blocked_table, {email, MapSet.new(blocked_emails), expiry})
    end

    test "a peer in the caller's block list is never matched" do
      # The blocked peer is the only one waiting — match should not pick
      # them; new@ui.ac.id should stay queued instead.
      MatchmakingServer.join_queue("blocked@itb.ac.id", "ITB", nil)

      seed_blocked_cache("new@ui.ac.id", ["blocked@itb.ac.id"])

      assert {:queued, _pos} = MatchmakingServer.join_queue("new@ui.ac.id", "UI", nil)
    end

    test "if the only candidate is blocked, caller waits instead of pairing" do
      MatchmakingServer.join_queue("a@itb.ac.id", "ITB", nil)
      MatchmakingServer.join_queue("b@ugm.ac.id", "UGM", nil)

      # Wait, then ensure a subsequent caller blocked against `a` falls
      # back to `b`, not `a`, even if `a` is the cross-uni candidate.
      # new@c uses UI; a@ is ITB (cross-uni, preferred), b@ is UGM (also
      # cross-uni). Both score equally on uni_score; either could win
      # the Enum.max_by tie. So we instead make `a` the *only* candidate
      # to assert exclusion yields a non-match.
      MatchmakingServer.leave_queue("b@ugm.ac.id")

      seed_blocked_cache("newer@ui.ac.id", ["a@itb.ac.id"])

      assert {:queued, _} = MatchmakingServer.join_queue("newer@ui.ac.id", "UI", nil)
    end

    test "a peer NOT in the caller's block list still matches normally" do
      MatchmakingServer.join_queue("ok_peer@itb.ac.id", "ITB", nil)

      seed_blocked_cache("new@ui.ac.id", ["someone_else@ui.ac.id"])

      assert {:matched, _pair_id, "ok_peer@itb.ac.id", _peer_university} =
               MatchmakingServer.join_queue("new@ui.ac.id", "UI", nil)
    end
  end
end
