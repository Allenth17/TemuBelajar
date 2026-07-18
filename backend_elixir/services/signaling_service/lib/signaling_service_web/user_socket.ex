defmodule SignalingServiceWeb.UserSocket do
  use Phoenix.Socket

  channel("signaling:*", SignalingServiceWeb.SignalingChannel)
  channel("chat:*", SignalingServiceWeb.ChatChannel)

  @impl true
  def connect(%{"token" => token}, socket, _connect_info) do
    auth_service_url = Application.get_env(:signaling_service, :auth_service_url)

    # Phase 3.22 — bound the auth_service lookup so a slow/down auth
    # service can't pin a connect handler for 30s (the default HTTPoison
    # timeout). Fail fast; the client will reconnect / surface an error.
    # Combined with phase 2.3's X-Internal-Secret requirement on
    # /api/verify-token so this request is actually authorized to call
    # the endpoint at all.
    headers = [{"X-Internal-Secret", internal_secret()}]

    http_opts = [recv_timeout: 3_000, connect_timeout: 3_000]

    case HTTPoison.get("#{auth_service_url}/api/verify-token?token=#{token}", headers, http_opts) do
      {:ok, %HTTPoison.Response{status_code: 200, body: body}} ->
        case Jason.decode(body) do
          {:ok, %{"valid" => true, "email" => email}} ->
            {:ok, assign(socket, :email, email)}

          _ ->
            :error
        end

      _ ->
        :error
    end
  end

  def connect(_params, _socket, _connect_info), do: :error

  @impl true
  def id(socket), do: "signaling_socket:#{socket.assigns.email}"

  defp internal_secret do
    Application.get_env(:signaling_service, :internal_secret) ||
      "dev_internal_secret_replace_in_production"
  end
end
