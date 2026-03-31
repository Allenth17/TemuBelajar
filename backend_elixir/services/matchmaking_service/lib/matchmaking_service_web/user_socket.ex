defmodule MatchmakingServiceWeb.UserSocket do
  use Phoenix.Socket

  channel "matchmaking:*", MatchmakingServiceWeb.MatchmakingChannel

  @impl true
  def connect(%{"token" => token}, socket, _connect_info) do
    auth_service_url = Application.get_env(:matchmaking_service, :auth_service_url)

    case HTTPoison.get("#{auth_service_url}/api/verify-token?token=#{token}") do
      {:ok, %HTTPoison.Response{status_code: 200, body: body}} ->
        case Jason.decode(body) do
          {:ok, %{"valid" => true, "email" => email, "university" => university}} ->
            socket = socket
            |> assign(:email, email)
            |> assign(:university, university)
            {:ok, socket}

          _ ->
            :error
        end

      _ ->
        :error
    end
  end

  def connect(_params, _socket, _connect_info), do: :error

  @impl true
  def id(socket), do: "matchmaking_socket:#{socket.assigns.email}"
end
