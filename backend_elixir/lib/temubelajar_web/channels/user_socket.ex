defmodule TemuBelajarWeb.UserSocket do
  use Phoenix.Socket

  # Channels
  channel "matchmaking:lobby", TemuBelajarWeb.MatchmakingChannel
  channel "signaling:*", TemuBelajarWeb.SignalingChannel

  @impl true
  def connect(%{"token" => token}, socket, _connect_info) do
    case TemuBelajar.Accounts.get_email_by_token(token) do
      nil ->
        :error

      email ->
        {:ok, assign(socket, :email, email)}
    end
  end

  def connect(_params, _socket, _connect_info), do: :error

  @impl true
  def id(socket), do: "user_socket:#{socket.assigns.email}"
end
