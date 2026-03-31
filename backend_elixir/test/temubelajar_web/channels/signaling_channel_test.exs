defmodule TemuBelajarWeb.SignalingChannelTest do
  use TemuBelajarWeb.ChannelCase, async: false
  alias TemuBelajarWeb.UserSocket

  setup do
    pair_id = "test_pair_#{:rand.uniform(1000)}"
    socket1 = socket(UserSocket, "user_socket:p1@ui.ac.id", %{email: "p1@ui.ac.id"})
    socket2 = socket(UserSocket, "user_socket:p2@ui.ac.id", %{email: "p2@ui.ac.id"})
    %{socket1: socket1, socket2: socket2, pair_id: pair_id}
  end

  test "first peer gets waiting_for_peer", %{socket1: socket1, pair_id: pair_id} do
    {:ok, reply, _socket} = subscribe_and_join(socket1, TemuBelajarWeb.SignalingChannel, "signaling:#{pair_id}")
    assert reply.status == "waiting_for_peer"
    assert_push "ice_servers", %{ice_servers: _}
  end

  test "second peer gets connected and broadcasts peer_joined", %{socket1: socket1, socket2: socket2, pair_id: pair_id} do
    {:ok, _, _s1} = subscribe_and_join(socket1, TemuBelajarWeb.SignalingChannel, "signaling:#{pair_id}")
    {:ok, reply2, _s2} = subscribe_and_join(socket2, TemuBelajarWeb.SignalingChannel, "signaling:#{pair_id}")
    
    assert reply2.status == "connected"
    # Both should receive peer_joined
    assert_push "peer_joined", %{peer_count: 2, pair_id: ^pair_id}
  end

  test "third peer gets error full room", %{socket1: socket1, socket2: socket2, pair_id: pair_id} do
    subscribe_and_join(socket1, TemuBelajarWeb.SignalingChannel, "signaling:#{pair_id}")
    subscribe_and_join(socket2, TemuBelajarWeb.SignalingChannel, "signaling:#{pair_id}")

    socket3 = socket(UserSocket, "user_socket:p3@ui.ac.id", %{email: "p3@ui.ac.id"})
    assert {:error, %{reason: "Room penuh"}} = subscribe_and_join(socket3, TemuBelajarWeb.SignalingChannel, "signaling:#{pair_id}")
  end

  test "broadcasting offer, answer, and ice_candidate", %{socket1: socket1, socket2: socket2, pair_id: pair_id} do
    {:ok, _, s1} = subscribe_and_join(socket1, TemuBelajarWeb.SignalingChannel, "signaling:#{pair_id}")
    {:ok, _, s2} = subscribe_and_join(socket2, TemuBelajarWeb.SignalingChannel, "signaling:#{pair_id}")

    push(s1, "offer", %{"sdp" => "mock_offer"})
    assert_broadcast "offer", %{from: "p1@ui.ac.id", sdp: "mock_offer"}

    push(s2, "answer", %{"sdp" => "mock_answer"})
    assert_broadcast "answer", %{from: "p2@ui.ac.id", sdp: "mock_answer"}

    push(s1, "ice_candidate", %{"candidate" => "mock_ice", "sdp_mid" => "mock", "sdp_m_line_index" => 0})
    assert_broadcast "ice_candidate", %{from: "p1@ui.ac.id", candidate: "mock_ice"}
  end

  test "errors on oversized SDP", %{socket1: socket1, pair_id: pair_id} do
    {:ok, _, s1} = subscribe_and_join(socket1, TemuBelajarWeb.SignalingChannel, "signaling:#{pair_id}")
    huge_sdp = String.duplicate("a", 17_000)
    ref = push(s1, "offer", %{"sdp" => huge_sdp})
    assert_reply ref, :error, %{reason: "SDP terlalu besar"}
  end

  test "graceful disconnect emits peer_left", %{socket1: socket1, socket2: socket2, pair_id: pair_id} do
    {:ok, _, s1} = subscribe_and_join(socket1, TemuBelajarWeb.SignalingChannel, "signaling:#{pair_id}")
    {:ok, _, _s2} = subscribe_and_join(socket2, TemuBelajarWeb.SignalingChannel, "signaling:#{pair_id}")

    push(s1, "session_end", %{})
    assert_broadcast "peer_left", %{reason: "peer_ended_session", peer_email: "p1@ui.ac.id"}
  end

  test "ping responds pong", %{socket1: socket1, pair_id: pair_id} do
    {:ok, _, s1} = subscribe_and_join(socket1, TemuBelajarWeb.SignalingChannel, "signaling:#{pair_id}")
    ref = push(s1, "ping", %{})
    assert_reply ref, :ok, %{pong: true, time: _}
  end
  
  test "renegotiate broadcasts to peer", %{socket1: socket1, socket2: socket2, pair_id: pair_id} do
    {:ok, _, s1} = subscribe_and_join(socket1, TemuBelajarWeb.SignalingChannel, "signaling:#{pair_id}")
    {:ok, _, _s2} = subscribe_and_join(socket2, TemuBelajarWeb.SignalingChannel, "signaling:#{pair_id}")

    push(s1, "renegotiate", %{"sdp" => "mock"})
    assert_broadcast "renegotiate", %{from: "p1@ui.ac.id", sdp: "mock"}
  end
end
