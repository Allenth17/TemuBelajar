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
    %{"text" => "hello", "timestamp" => "2024-12-31T15:30:00.123Z"}

  Phase 3.17 — `timestamp` is now ISO 8601 (UTC, millisecond-precise) for API
  consistency with the rest of the backend (Ecto's `:utc_datetime` columns,
  e.g. `friend_requests.inserted_at`). Previously this used
  `:os.system_time(:millisecond)` (epoch Long), which forced every client to
  know two unrelated representations depending on which surface it touched.

  Events emitted:
    "msg"        — new message from partner
    "typing"     — partner is typing
    "chat_reset" — chat was cleared (Next pressed or peer left)
  """

  use Phoenix.Channel

  require Logger

  @max_message_length 1_000
  @max_emoji_bytes 200

  # Phase 0.7 — gate channel join on pair ownership, exactly like the
  # signaling channel. Without this an attacker could subscribe to
  # "chat:<guessed_pair_id>" and silently read the partner's chat.
  def join("chat:" <> pair_id, _payload, socket) do
    email = socket.assigns[:email]

    cond do
      is_nil(email) ->
        {:error, %{reason: "Unauthenticated"}}

      not SignalingServiceWeb.SignalingChannel.verify_pair_ownership?(pair_id, email) ->
        Logger.warn("[ChatChannel] Reject join pair=#{pair_id} email=#{email} — not owner")
        {:error, %{reason: "Pair not found or not owned by caller"}}

      true ->
        socket = assign(socket, :pair_id, pair_id)
        {:ok, %{pair_id: pair_id}, socket}
    end
  end

  # ── Text message ─────────────────────────────────────────────────────────────

  def handle_in("msg", %{"text" => text} = payload, socket)
      when byte_size(text) > @max_message_length do
    {:reply, {:error, %{reason: "Pesan terlalu panjang (max #{@max_message_length} karakter)"}},
     socket}
  end

  def handle_in("msg", %{"text" => text} = _payload, socket) do
    broadcast_from!(socket, "msg", %{
      text: text,
      from: socket.assigns.email,
      # Phase 3.17 — ISO 8601 UTC string, consistent with Ecto's
      # `:utc_datetime` columns across the backend. Client parses with
      # `kotlinx.datetime.Instant.parse(...)` (Android/JS/WASM/Native all
      # share the same IMPL).
      timestamp: DateTime.utc_now() |> DateTime.truncate(:millisecond) |> DateTime.to_iso8601()
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
      # Phase 3.17 — see "msg" handler for rationale.
      timestamp: DateTime.utc_now() |> DateTime.truncate(:millisecond) |> DateTime.to_iso8601()
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
