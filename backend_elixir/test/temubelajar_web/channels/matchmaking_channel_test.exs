defmodule TemuBelajarWeb.MatchmakingChannelTest do
  use TemuBelajarWeb.ChannelCase, async: false
  alias TemuBelajarWeb.UserSocket
  alias TemuBelajar.Realtime.MatchmakingServer

  setup do
    # Clear GenServer state
    :sys.replace_state(MatchmakingServer, fn _ -> %MatchmakingServer{queue: [], pairs: %{}} end)
    
    {:ok, _, socket} =
      socket(UserSocket, "user_socket:test@ui.ac.id", %{email: "test@ui.ac.id"})
      |> subscribe_and_join(TemuBelajarWeb.MatchmakingChannel, "matchmaking:lobby")

    %{socket: socket}
  end

  test "ping replies with status pong", %{socket: socket} do
    ref = push(socket, "ping", %{})
    assert_reply ref, :ok, %{status: "pong", time: _}
  end

  test "join_queue replies with queued status", %{socket: socket} do
    ref = push(socket, "join_queue", %{"university" => "UI"})
    assert_reply ref, :ok, %{status: "queued", position: 1}
  end

  test "leave_queue replies with left status", %{socket: socket} do
    push(socket, "join_queue", %{"university" => "UI"})
    ref = push(socket, "leave_queue", %{})
    assert_reply ref, :ok, %{status: "left"}
  end

  test "receives match_found when another peer joins", %{socket: socket} do
    ref = push(socket, "join_queue", %{"university" => "UI"})
    assert_reply ref, :ok, %{status: "queued"}

    send(socket.channel_pid, {:match_as_receiver, "dummy_pair_id", "peer@ui.ac.id"})
    assert_push "match_found", %{role: "receiver", peer_email: "peer@ui.ac.id"}
  end

  test "receives queue_stats broadcast when someone joins", %{socket: socket} do
    # socket is already joined and subscribed to "matchmaking:stats"
    # assert the subscription queue size
    assert socket.assigns.email == "test@ui.ac.id"
    
    MatchmakingServer.join_queue("other@ui.ac.id", "UI")
    assert_push "queue_stats", %{queue_size: 1}
  end
  
  test "receives queue_timeout broadcast when heartbeat expires", %{socket: _socket} do
    # Join queue using a mocked old timestamp
    old_ts = System.monotonic_time(:millisecond) - 65_000
    :sys.replace_state(MatchmakingServer, fn state ->
      %{state | queue: [{"test@ui.ac.id", "UI", old_ts}]}
    end)

    send(MatchmakingServer, :heartbeat)
    :sys.get_state(MatchmakingServer)
    assert_push "queue_timeout", %{message: "Waktu mencari habis, coba lagi"}
  end
end
