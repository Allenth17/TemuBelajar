defmodule ApiGatewayWeb.GatewayController do
  use ApiGatewayWeb, :controller

  alias ApiGateway.Services
  # ──────────────────────────── Health Check ───────────────────────────────

  def health(conn, _params) do
    json(conn, %{status: "ok", service: "api_gateway"})
  end

  # ──────────────────────────── Auth Endpoints ─────────────────────────────

  def register(conn, params) do
    proxy_request(conn, :auth_service, :post, "/api/register", params)
  end

  def verify_otp(conn, params) do
    proxy_request(conn, :auth_service, :post, "/api/verify-otp", params)
  end

  def resend_otp(conn, params) do
    proxy_request(conn, :auth_service, :post, "/api/resend-otp", params)
  end

  def login(conn, params) do
    proxy_request(conn, :auth_service, :post, "/api/login", params)
  end

  def logout(conn, params) do
    proxy_request(conn, :auth_service, :post, "/api/logout", params)
  end

  def me(conn, params) do
    proxy_request(conn, :auth_service, :get, "/api/me", params)
  end

  def cleanup_sessions(conn, params) do
    proxy_request(conn, :auth_service, :delete, "/api/expired-sessions", params)
  end

  # ──────────────────────────── User Endpoints ─────────────────────────────

  def list_users(conn, params) do
    proxy_request(conn, :user_service, :get, "/api/users", params)
  end

  def search_users(conn, %{"q" => _} = params) do
    proxy_request(conn, :user_service, :get, "/api/users/search", params)
  end
  def search_users(conn, params) do
    proxy_request(conn, :user_service, :get, "/api/users/search", params)
  end

  def get_user(conn, %{"email" => email}) do
    proxy_request(conn, :user_service, :get, "/api/user/#{email}", %{})
  end

  def update_user(conn, %{"email" => email} = params) do
    proxy_request(conn, :user_service, :put, "/api/user/#{email}", params)
  end

  # ──────────────────────────── Signaling Endpoints ───────────────────────────

  def signaling_join(conn, params) do
    proxy_request(conn, :signaling_service, :post, "/api/signaling/join", params)
  end

  def signaling_offer(conn, params) do
    proxy_request(conn, :signaling_service, :post, "/api/signaling/offer", params)
  end

  def signaling_answer(conn, params) do
    proxy_request(conn, :signaling_service, :post, "/api/signaling/answer", params)
  end

  def signaling_ice(conn, params) do
    proxy_request(conn, :signaling_service, :post, "/api/signaling/ice", params)
  end

  # ──────────────────────────── Matchmaking Endpoints ───────────────────────

  def matchmaking_join(conn, params) do
    proxy_request(conn, :matchmaking_service, :post, "/api/matchmaking/join", params)
  end

  def matchmaking_leave(conn, params) do
    proxy_request(conn, :matchmaking_service, :post, "/api/matchmaking/leave", params)
  end

  # ──────────────────────────── Social Endpoints ────────────────────────────

  def social_follow(conn, params),
    do: proxy_social(conn, :post, "/api/social/follow", params)

  def social_unfollow(conn, %{"target" => target} = params),
    do: proxy_social(conn, :delete, "/api/social/follow/#{target}", params)

  def social_followers(conn, %{"email" => e} = params),
    do: proxy_social(conn, :get, "/api/social/followers/#{e}", params)

  def social_following(conn, %{"email" => e} = params),
    do: proxy_social(conn, :get, "/api/social/following/#{e}", params)

  def social_profile(conn, %{"email" => e} = params),
    do: proxy_social(conn, :get, "/api/social/profile/#{e}", params)

  def social_send_friend_request(conn, params),
    do: proxy_social(conn, :post, "/api/social/friend-request", params)

  def social_respond_friend_request(conn, %{"from" => from} = params),
    do: proxy_social(conn, :put, "/api/social/friend-request/#{from}", params)

  def social_unfriend(conn, %{"target" => target} = params),
    do: proxy_social(conn, :delete, "/api/social/friend/#{target}", params)

  def social_friends(conn, %{"email" => e} = params),
    do: proxy_social(conn, :get, "/api/social/friends/#{e}", params)

  def social_pending_requests(conn, params),
    do: proxy_social(conn, :get, "/api/social/friend-requests/pending", params)

  def social_block(conn, params),
    do: proxy_social(conn, :post, "/api/social/block", params)

  def social_unblock(conn, %{"target" => target} = params),
    do: proxy_social(conn, :delete, "/api/social/block/#{target}", params)

  def social_report(conn, params),
    do: proxy_social(conn, :post, "/api/social/report", params)

  # Social proxy — injects x-caller-email header (derived from Bearer token via auth service)
  defp proxy_social(conn, method, path, params) do
    url = Services.get_service_url(:social_service)
    full_url = url <> path

    headers = [{"Content-Type", "application/json"}]

    headers = case get_req_header(conn, "authorization") do
      [auth | _] -> [{"Authorization", auth} | headers]
      [] -> headers
    end

    # Forward caller email for social service auth (set by auth service validation)
    headers = case get_req_header(conn, "x-caller-email") do
      [email | _] -> [{"X-Caller-Email", email} | headers]
      [] -> headers
    end

    request_body = if method == :get, do: "", else: Jason.encode!(params)

    case HTTPoison.request(method, full_url, request_body, headers) do
      {:ok, %HTTPoison.Response{status_code: status, body: body}} ->
        conn |> put_status(status) |> json(decode_body(body))
      {:error, %HTTPoison.Error{reason: reason}} ->
        conn |> put_status(503) |> json(%{error: "Social service unavailable", reason: to_string(reason)})
    end
  end

  # ──────────────────────────── Proxy Helper ───────────────────────────────

  defp proxy_request(conn, service, method, path, params) do
    url = Services.get_service_url(service)
    full_url = url <> path

    headers = [
      {"Content-Type", "application/json"}
    ]

    # Forward authorization header if present
    headers = case get_req_header(conn, "authorization") do
      [] -> headers
      [auth | _] -> [{"Authorization", auth} | headers]
    end

    request_body = if method == :get, do: "", else: Jason.encode!(params)

    case HTTPoison.request(method, full_url, request_body, headers) do
      {:ok, %HTTPoison.Response{status_code: status, body: body}} ->
        conn
        |> put_status(status)
        |> json(decode_body(body))

      {:error, %HTTPoison.Error{reason: reason}} ->
        conn
        |> put_status(503)
        |> json(%{error: "Service unavailable", reason: to_string(reason)})
    end
  end

  defp decode_body(""), do: %{}
  defp decode_body(body) do
    case Jason.decode(body) do
      {:ok, decoded} -> decoded
      {:error, _} -> %{raw_body: body}
    end
  end
end
