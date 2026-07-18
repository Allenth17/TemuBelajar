defmodule ApiGatewayWeb.UserSocket do
  use Phoenix.Socket

  ## Channels
  channel("signaling:*", ApiGatewayWeb.SignalingProxyChannel)
  channel("matchmaking:*", ApiGatewayWeb.MatchmakingProxyChannel)
  channel("chat:*", ApiGatewayWeb.ChatProxyChannel)

  # Validate the Bearer token against auth_service on connect and assign the
  # verified `:email` so SignalingProxyChannel / MatchmakingProxyChannel /
  # ChatProxyChannel can attribute messages safely (see 0.6). Any request
  # without a valid token is rejected here.
  @impl true
  def connect(%{"token" => token}, socket, connect_info) when byte_size(token) > 0 do
    peer_data = Map.get(connect_info, :peer_data)

    case ApiGateway.AuthBridge.resolve_email(token) do
      {:ok, email} ->
        {:ok,
         socket
         |> assign(:peer_data, peer_data)
         |> assign(:token, token)
         |> assign(:email, email)}

      {:error, _reason} ->
        :error
    end
  end

  def connect(_params, _socket, _connect_info), do: :error

  # Returning a stable socket id per email lets us broadcast "disconnect"
  # from logout / token-revocation flows and terminate the active socket
  # (see 7.19) — stolen tokens can no longer persist after logout.
  @impl true
  def id(socket), do: "users_socket:#{socket.assigns[:email]}"
end
