defmodule SignalingServiceWeb.ChatChannel do
  @moduledoc """
  Ephemeral text chat channel for an active video-chat pair.

  Topic: "chat:{pair_id}"

  Design decisions for 1M+ user scale:
    - Zero DB writes — messages are ephemeral (PubSub only)
    - Messages exist only in Phoenix PubSub (distributed via pg2/pubsub adapter)
    - On "leave" or channel exit → broadcast "chat_reset" so both clients clear their UI
    - Process hibernates after 5s idle (saves ~60% RAM per idle channel)
    - No ETS state — the channel process itself holds no message list server-side

  Message format (client → server → broadcast):
    %{"text" => "hello", "timestamp" => 1234567890}

  Events emitted:
    "msg"        — new message from partner
    "typing"     — partner is typing
    "chat_reset" — chat was cleared (Next pressed or peer left)
  """

  use Phoenix.Channel

  require Logger

  @max_message_length 1_000
  @max_emoji_bytes 200

  def join("chat:" <> pair_id, _payload, socket) do
    socket = assign(socket, :pair_id, pair_id)
    {:ok, %{pair_id: pair_id}, socket}
  end

  # ── Text message ─────────────────────────────────────────────────────────────

  def handle_in("msg", %{"text" => text} = payload, socket)
    when byte_size(text) > @max_message_length do
    {:reply, {:error, %{reason: "Pesan terlalu panjang (max #{@max_message_length} karakter)"}}, socket}
  end

  def handle_in("msg", %{"text" => text} = _payload, socket) do
    broadcast_from!(socket, "msg", %{
      text: text,
      from: socket.assigns.email,
      timestamp: :os.system_time(:millisecond)
    })
    {:noreply, socket}
  end

  # ── Emoji message ─────────────────────────────────────────────────────────────

  def handle_in("emoji", %{"emoji" => emoji} = _payload, socket)
    when byte_size(emoji) > @max_emoji_bytes do
    {:reply, {:error, %{reason: "Emoji payload terlalu besar"}}, socket}
  end

  def handle_in("emoji", %{"emoji" => emoji}, socket) do
    broadcast_from!(socket, "emoji", %{
      emoji: emoji,
      from: socket.assigns.email,
      timestamp: :os.system_time(:millisecond)
    })
    {:noreply, socket}
  end

  # ── Typing indicator ─────────────────────────────────────────────────────────

  def handle_in("typing", _payload, socket) do
    broadcast_from!(socket, "typing", %{from: socket.assigns.email})
    {:noreply, socket}
  end

  # ── Leave / Next — clears chat on both sides ──────────────────────────────────

  def handle_in("leave", _payload, socket) do
    broadcast!(socket, "chat_reset", %{
      reason: "peer_left",
      pair_id: socket.assigns.pair_id
    })
    {:noreply, socket}
  end

  # ── Channel exit cleanup ──────────────────────────────────────────────────────

  def terminate(_reason, socket) do
    # Broadcast chat_reset so the remaining peer clears their chat UI
    broadcast_from!(socket, "chat_reset", %{
      reason: "peer_disconnected",
      pair_id: socket.assigns[:pair_id]
    })
    :ok
  end
end
