defmodule ApiGatewayWeb.ChatProxyChannel do
  @moduledoc """
  WebSocket channel proxy for in-call chat (text messages, emojis, typing indicators).

  All user-generated events use broadcast_from! so the sender does NOT
  receive their own message echoed back (the frontend adds messages
  optimistically on send).
  """

  use ApiGatewayWeb, :channel
  require Logger

  @max_msg_bytes 1_000
  @max_emoji_bytes 200

  @impl true
  def join("chat:" <> pair_id, _payload, socket) do
    Logger.info("[ChatProxy] #{socket.assigns[:email]} joined chat:#{pair_id}")
    {:ok, assign(socket, :pair_id, pair_id)}
  end

  # Text message — relay only to the OTHER peer
  @impl true
  def handle_in("msg", %{"text" => text} = payload, socket)
      when byte_size(text) <= @max_msg_bytes do
    broadcast_from!(socket, "msg", payload)
    {:noreply, socket}
  end

  @impl true
  def handle_in("msg", _, socket) do
    {:reply, {:error, %{reason: "Message too long"}}, socket}
  end

  # Emoji — relay only to the OTHER peer
  @impl true
  def handle_in("emoji", %{"emoji" => emoji} = payload, socket)
      when byte_size(emoji) <= @max_emoji_bytes do
    broadcast_from!(socket, "emoji", payload)
    {:noreply, socket}
  end

  @impl true
  def handle_in("emoji", _, socket) do
    {:reply, {:error, %{reason: "Emoji payload too large"}}, socket}
  end

  # Typing indicator — relay only to OTHER peer (no ack needed)
  @impl true
  def handle_in("typing", payload, socket) do
    broadcast_from!(socket, "typing", payload)
    {:noreply, socket}
  end

  # Graceful leave — stop this channel process cleanly
  @impl true
  def handle_in("leave", _payload, socket) do
    {:stop, :normal, socket}
  end

  @impl true
  def handle_in(_event, _payload, socket) do
    {:noreply, socket}
  end

  @impl true
  def handle_info(%Phoenix.Socket.Broadcast{event: event, payload: payload}, socket) do
    push(socket, event, payload)
    {:noreply, socket}
  end

  def handle_info(_, socket), do: {:noreply, socket}
end
