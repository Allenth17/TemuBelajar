defmodule ApiGatewayWeb.MatchmakingProxyChannel do
  @moduledoc """
  WebSocket channel proxy for matchmaking.

  Flow (for the WAITING peer — the one already in queue):
    1. Client connects WS → phx_join "matchmaking:lobby"
    2. join/3 authenticates via auth service, subscribes to personal notification topic
    3. Client sends "join_queue" with optional university/major
    4. Gateway HTTP-POSTs to matchmaking service
    5. If immediate match: gateway pushes "match_found" directly from this callback
    6. If queued: client waits; matchmaking service HTTP-POSTs to gateway's
       /api/internal/notify/:email when a future match is found — that handler
       broadcasts to "matchmaking:user:<email>" topic which this channel receives
       via handle_info/2
  """

  use ApiGatewayWeb, :channel
  require Logger

  def join("matchmaking:lobby", _payload, socket) do
    token = socket.assigns[:token]
    email = get_email_from_auth(token)

    if email do
      Logger.info("[MatchmakingProxy] #{email} joined matchmaking:lobby")
      # Subscribe to personal notification topic (pushed by /api/internal/notify)
      ApiGatewayWeb.Endpoint.subscribe("matchmaking:user:#{email}")
      {:ok, assign(socket, :email, email)}
    else
      Logger.error("[MatchmakingProxy] Auth failed for token #{inspect(token)}")
      {:error, %{reason: "unauthorized"}}
    end
  end

  def handle_in("join_queue", payload, socket) do
    email = socket.assigns[:email]
    matchmaking_url = Application.get_env(:api_gateway, :matchmaking_service_url)
    gateway_url = Application.get_env(:api_gateway, :self_url, "http://localhost:4000")

    body =
      payload
      |> Map.put("email", email)
      # Tell matchmaking service where to callback for async match notifications
      |> Map.put("notify_url", "#{gateway_url}/api/internal/notify/#{URI.encode(email)}")

    Logger.info("[MatchmakingProxy] #{email} joining queue: #{inspect(body)}")

    case HTTPoison.post(
           "#{matchmaking_url}/api/matchmaking/join",
           Jason.encode!(body),
           [{"Content-Type", "application/json"}],
           recv_timeout: 10_000
         ) do
      {:ok, %{status_code: 200, body: res_body}} ->
        case Jason.decode(res_body) do
          {:ok, %{"status" => "matched"} = reply} ->
            # Immediate match — push directly to the caller
            push(socket, "match_found", %{
              pair_id: reply["pair_id"],
              peer_email: reply["peer_email"],
              peer_university: reply["peer_university"] || "",
              role: "caller"
            })

            {:reply, {:ok, %{status: "matched"}}, socket}

          {:ok, %{"status" => "queued"} = reply} ->
            {:reply, {:ok, %{status: "queued", position: reply["position"]}}, socket}

          {:ok, reply} ->
            {:reply, {:ok, reply}, socket}

          {:error, _} ->
            {:reply, {:error, %{reason: "invalid_response"}}, socket}
        end

      {:error, %HTTPoison.Error{reason: reason}} ->
        Logger.error("[MatchmakingProxy] HTTP error: #{inspect(reason)}")
        {:reply, {:error, %{reason: "service_unavailable"}}, socket}

      _ ->
        {:reply, {:error, %{reason: "failed_to_join"}}, socket}
    end
  end

  def handle_in("leave_queue", _payload, socket) do
    email = socket.assigns[:email]
    matchmaking_url = Application.get_env(:api_gateway, :matchmaking_service_url)
    Logger.info("[MatchmakingProxy] #{email} leaving queue")
    ApiGatewayWeb.Endpoint.unsubscribe("matchmaking:user:#{email}")

    Task.start(fn ->
      HTTPoison.post(
        "#{matchmaking_url}/api/matchmaking/leave",
        Jason.encode!(%{email: email}),
        [{"Content-Type", "application/json"}]
      )
    end)

    {:noreply, socket}
  end

  # ── Async notifications from matchmaking service (via /api/internal/notify) ──

  # Match found for the WAITING peer — pushed by NotifyController
  def handle_info(%Phoenix.Socket.Broadcast{event: "match_found", payload: payload}, socket) do
    Logger.info("[MatchmakingProxy] Async match_found for #{socket.assigns[:email]}")

    push(socket, "match_found", %{
      pair_id: Map.get(payload, :pair_id) || Map.get(payload, "pair_id"),
      peer_email: Map.get(payload, :peer_email) || Map.get(payload, "peer_email"),
      peer_university:
        Map.get(payload, :peer_university) || Map.get(payload, "peer_university") || "",
      role: Map.get(payload, :role) || Map.get(payload, "role") || "receiver"
    })

    {:noreply, socket}
  end

  def handle_info(%Phoenix.Socket.Broadcast{event: "queue_timeout"}, socket) do
    push(socket, "queue_timeout", %{message: "Waktu mencari habis, coba lagi"})
    {:noreply, socket}
  end

  def handle_info(%Phoenix.Socket.Broadcast{event: "queue_stats", payload: payload}, socket) do
    push(socket, "queue_stats", payload)
    {:noreply, socket}
  end

  def handle_info(_, socket), do: {:noreply, socket}

  def terminate(_reason, socket) do
    email = socket.assigns[:email]

    if email do
      ApiGatewayWeb.Endpoint.unsubscribe("matchmaking:user:#{email}")
      matchmaking_url = Application.get_env(:api_gateway, :matchmaking_service_url)

      Task.start(fn ->
        HTTPoison.post(
          "#{matchmaking_url}/api/matchmaking/leave",
          Jason.encode!(%{email: email}),
          [{"Content-Type", "application/json"}]
        )
      end)
    end

    :ok
  end

  # ── Private ─────────────────────────────────────────────────────────────────

  defp get_email_from_auth(nil), do: nil

  defp get_email_from_auth(token) do
    auth_url = Application.get_env(:api_gateway, :auth_service_url)

    case HTTPoison.get(
           "#{auth_url}/api/verify-token?token=#{URI.encode_www_form(token)}",
           [],
           recv_timeout: 5_000
         ) do
      {:ok, %{status_code: 200, body: body}} ->
        case Jason.decode(body) do
          {:ok, %{"valid" => true, "email" => email}} -> email
          _ -> nil
        end

      _ ->
        nil
    end
  end
end
