defmodule TemuBelajar.Realtime.MatchmakingServerTest do
  use ExUnit.Case, async: false
  alias TemuBelajar.Realtime.MatchmakingServer

  setup do
    # Reset state to clear any running queues
    :sys.replace_state(MatchmakingServer, fn _ -> %MatchmakingServer{queue: [], pairs: %{}} end)
    :ok
  end

  test "queue_size returns 0 initially" do
    assert MatchmakingServer.queue_size() == 0
  end

  test "join_queue returns queued if empty" do
    assert {:queued, 1} = MatchmakingServer.join_queue("test1@ui.ac.id", "UI")
    assert MatchmakingServer.queue_size() == 1
  end

  test "join_queue with same email returns current position instead of duplicating" do
    MatchmakingServer.join_queue("test1@ui.ac.id", "UI")
    assert {:queued, 1} = MatchmakingServer.join_queue("test1@ui.ac.id", "UI")
    assert MatchmakingServer.queue_size() == 1
  end

  test "join_queue matches with existing peer of same university" do
    MatchmakingServer.join_queue("p1@ui.ac.id", "UI")
    assert {:matched, pair_id, "p1@ui.ac.id"} = MatchmakingServer.join_queue("p2@ui.ac.id", "UI")
    assert MatchmakingServer.queue_size() == 0
    
    # State inspection
    state = :sys.get_state(MatchmakingServer)
    assert Map.has_key?(state.pairs, pair_id)
  end

  test "join_queue matches cross-university if no same university found" do
    MatchmakingServer.join_queue("p1@ui.ac.id", "UI")
    assert {:matched, pair_id, "p1@ui.ac.id"} = MatchmakingServer.join_queue("p2@itb.ac.id", "ITB")
    assert MatchmakingServer.queue_size() == 0
    
    state = :sys.get_state(MatchmakingServer)
    assert Map.has_key?(state.pairs, pair_id)
  end

  test "leave_queue removes user from queue" do
    MatchmakingServer.join_queue("p1@ui.ac.id", "UI")
    assert MatchmakingServer.queue_size() == 1
    MatchmakingServer.leave_queue("p1@ui.ac.id")
    # Using :sys.get_state to ensure async cast finishes
    :sys.get_state(MatchmakingServer)
    assert MatchmakingServer.queue_size() == 0
  end

  test "end_pair removes pairing from tracked sessions" do
    MatchmakingServer.join_queue("p1@ui.ac.id", "UI")
    {:matched, pair_id, _} = MatchmakingServer.join_queue("p2@ui.ac.id", "UI")
    MatchmakingServer.end_pair(pair_id)
    
    state = :sys.get_state(MatchmakingServer)
    refute Map.has_key?(state.pairs, pair_id)
  end

  test "heartbeat expires old queues" do
    old_ts = System.monotonic_time(:millisecond) - 65_000
    :sys.replace_state(MatchmakingServer, fn state ->
      %{state | queue: [{"old@ui.ac.id", "UI", old_ts}, {"new@ui.ac.id", "UI", System.monotonic_time(:millisecond)}]}
    end)
    
    assert MatchmakingServer.queue_size() == 2
    send(MatchmakingServer, :heartbeat)
    
    state = :sys.get_state(MatchmakingServer)
    assert length(state.queue) == 1
    [{email, "UI", _}] = state.queue
    assert email == "new@ui.ac.id"
  end
end
