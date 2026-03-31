defmodule SignalingServiceWeb.SignalingChannelTest do
  @moduledoc """
  Tests for the SignalingChannel WebSocket channel.

  These tests exercise the channel's event handlers (offer, answer, ice, leave,
  heartbeat) and the ETS-based peer tracking. Authentication is bypassed by
  assigning :email directly on the socket.
  """

  use ExUnit.Case, async: false

  import Phoenix.ChannelTest

  @endpoint SignalingServiceWeb.Endpoint

  # ── Helpers ────────────────────────────────────────────────────────────────

  defp fresh_pair_id, do: "test_pair_#{System.unique_integer([:positive])}"

  # Builds a socket with email pre-assigned (no HTTP auth call)
  defp make_socket(email) do
    %Phoenix.Socket{
      endpoint: @endpoint,
      pubsub_server: SignalingService.PubSub,
      handler: SignalingServiceWeb.UserSocket,
      transport: :test,
      assigns: %{email: email, last_heartbeat: DateTime.utc_now()}
    }
  end

  setup do
    case :ets.whereis(:signaling_peers) do
      :undefined -> :ets.new(:signaling_peers, [:named_table, :public, :bag])
      _ -> :ets.delete_all_objects(:signaling_peers)
    end

    :ok
  end

  # ── join/3 ─────────────────────────────────────────────────────────────────

  describe "join signaling channel" do
    test "first peer joins and waits" do
      pair_id = fresh_pair_id()
      socket = make_socket("alice@ui.ac.id")

      {:ok, reply, _socket} =
        Phoenix.ChannelTest.subscribe_and_join(socket, "signaling:#{pair_id}", %{})

      assert reply.status == "waiting_for_peer"
    end

    test "second peer joins and gets connected status" do
      pair_id = fresh_pair_id()
      socket_a = make_socket("alice@ui.ac.id")
      socket_b = make_socket("bob@itb.ac.id")

      {:ok, _reply_a, _sock_a} =
        Phoenix.ChannelTest.subscribe_and_join(socket_a, "signaling:#{pair_id}", %{})

      {:ok, reply_b, _sock_b} =
        Phoenix.ChannelTest.subscribe_and_join(socket_b, "signaling:#{pair_id}", %{})

      assert reply_b.status == "connected"
    end

    test "third peer is rejected (room full)" do
      pair_id = fresh_pair_id()
      # Manually pre-populate ETS with 2 peers
      :ets.insert(:signaling_peers, {pair_id, "a@ui.ac.id"})
      :ets.insert(:signaling_peers, {pair_id, "b@itb.ac.id"})

      socket_c = make_socket("carol@ugm.ac.id")

      result =
        Phoenix.ChannelTest.subscribe_and_join(socket_c, "signaling:#{pair_id}", %{})

      assert {:error, %{reason: "Room penuh"}} = result
    end
  end

  # ── handle_in("offer") ─────────────────────────────────────────────────────

  describe "offer event" do
    test "broadcasts offer to other peer" do
      pair_id = fresh_pair_id()
      socket = make_socket("alice@ui.ac.id")

      {:ok, _, joined_socket} =
        Phoenix.ChannelTest.subscribe_and_join(socket, "signaling:#{pair_id}", %{})

      sdp = "v=0\r\no=alice 1 1 IN IP4 127.0.0.1\r\n"
      ref = push(joined_socket, "offer", %{"sdp" => sdp})

      # Sending an offer should succeed (no reply — broadcast_from!)
      refute_reply(ref, :error)
    end

    test "rejects oversized SDP (> 16KB)" do
      pair_id = fresh_pair_id()
      socket = make_socket("alice@ui.ac.id")
      {:ok, _, joined_socket} =
        Phoenix.ChannelTest.subscribe_and_join(socket, "signaling:#{pair_id}", %{})

      huge_sdp = String.duplicate("x", 20_000)
      ref = push(joined_socket, "offer", %{"sdp" => huge_sdp})

      assert_reply ref, :error, %{reason: "SDP terlalu besar"}
    end
  end

  # ── handle_in("answer") ────────────────────────────────────────────────────

  describe "answer event" do
    test "rejects oversized answer SDP" do
      pair_id = fresh_pair_id()
      socket = make_socket("bob@itb.ac.id")
      {:ok, _, joined_socket} =
        Phoenix.ChannelTest.subscribe_and_join(socket, "signaling:#{pair_id}", %{})

      huge_sdp = String.duplicate("y", 20_000)
      ref = push(joined_socket, "answer", %{"sdp" => huge_sdp})

      assert_reply ref, :error, %{reason: "SDP terlalu besar"}
    end

    test "valid answer SDP is forwarded" do
      pair_id = fresh_pair_id()
      socket = make_socket("bob@itb.ac.id")
      {:ok, _, joined_socket} =
        Phoenix.ChannelTest.subscribe_and_join(socket, "signaling:#{pair_id}", %{})

      sdp = "v=0\r\no=bob 2 2 IN IP4 127.0.0.1\r\n"
      ref = push(joined_socket, "answer", %{"sdp" => sdp})
      refute_reply(ref, :error)
    end
  end

  # ── handle_in("ice") ───────────────────────────────────────────────────────

  describe "ice candidate event" do
    test "valid ICE candidate is forwarded" do
      pair_id = fresh_pair_id()
      socket = make_socket("alice@ui.ac.id")
      {:ok, _, joined_socket} =
        Phoenix.ChannelTest.subscribe_and_join(socket, "signaling:#{pair_id}", %{})

      ref = push(joined_socket, "ice", %{
        "candidate" => "candidate:1 1 udp 2130706431 127.0.0.1 55000 typ host",
        "sdpMid" => "0",
        "sdpMLineIndex" => 0
      })
      refute_reply(ref, :error)
    end
  end

  # ── handle_in("leave") ─────────────────────────────────────────────────────

  describe "leave event" do
    test "emits peer_left event after leave" do
      pair_id = fresh_pair_id()
      socket = make_socket("alice@ui.ac.id")
      {:ok, _, joined_socket} =
        Phoenix.ChannelTest.subscribe_and_join(socket, "signaling:#{pair_id}", %{})

      push(joined_socket, "leave", %{})

      # Peer_left should be broadcast to the channel topic
      assert_broadcast "peer_left", %{reason: "peer_ended_session"}, 500
    end
  end

  # ── handle_in("heartbeat") ─────────────────────────────────────────────────

  describe "heartbeat" do
    test "heartbeat is accepted without reply" do
      pair_id = fresh_pair_id()
      socket = make_socket("alice@ui.ac.id")
      {:ok, _, joined_socket} =
        Phoenix.ChannelTest.subscribe_and_join(socket, "signaling:#{pair_id}", %{})

      ref = push(joined_socket, "heartbeat", %{})
      refute_reply(ref, :error)
    end
  end
end
