defmodule ApiGatewayWeb.GatewayController do
  use ApiGatewayWeb, :controller

  alias ApiGateway.Services
  alias ApiGateway.AuthBridge

  # HTTP options for downstream service calls — bounded so slow downstreams
  # can't pin a gateway worker (see 3.21 / 3.22).
  @downstream_opts [recv_timeout: 5_000, connect_timeout: 3_000]

  # Phase 3.3 — single error envelope helper. Every gateway ERROR response
  # now goes through this so the shape is uniformly `%{error: "msg"}` (the
  # only shape the frontend parses). Drop-in replacement for ad-hoc
  # `%{error: ..., reason: ...}` / `%{valid: false, ...}` shapes that
  # previously escaped from the proxy path on downstream failure.
  def render_error(conn, status, message) when is_binary(message) do
    conn |> put_status(status) |> json(%{error: message})
  end

  # ──────────────────────────── Health Check ───────────────────────────────

  def health(conn, _params) do
    json(conn, %{status: "ok", service: "api_gateway"})
  end

  # ──────────────────────────── Auth Endpoints ─────────────────────────────
  # Auth endpoints proxy to auth_service — they are public (login/register/etc).
  # The Authorization header (when present) is forwarded verbatim so that
  # /logout and /me can be authorized inside auth_service.

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
  # user_service endpoints are protected — the gateway derives the caller's
  # email from the Bearer and forwards it via X-Caller-Email so user_service
  # can authorize ownership (see 0.4).

  def list_users(conn, params) do
    proxy_authenticated(conn, :user_service, :get, "/api/users", params)
  end

  def search_users(conn, %{"q" => _} = params) do
    proxy_authenticated(conn, :user_service, :get, "/api/users/search", params)
  end

  def search_users(conn, params) do
    proxy_authenticated(conn, :user_service, :get, "/api/users/search", params)
  end

  def get_user(conn, %{"email" => email}) do
    proxy_authenticated(conn, :user_service, :get, "/api/user/#{email}", %{})
  end

  def update_user(conn, %{"email" => email} = params) do
    proxy_authenticated(conn, :user_service, :put, "/api/user/#{email}", params)
  end

  # ──────────────────────────── Signaling Endpoints ───────────────────────────
  # NOTE: signaling is WebSocket-only via the gateway's SignalingProxyChannel.
  # The HTTP routes below all returned 404 from signaling_service (see 7.5)
  # and are removed to avoid false-positive CI. If a REST fallback is needed
  # implement it in signaling_service first.

  # ──────────────────────────── Matchmaking Endpoints ───────────────────────
  # matchmaking HTTP join/leave — the gateway overwrites the body's `email`
  # with the verified caller email so clients cannot impersonate (see 0.3).

  def matchmaking_join(conn, params) do
    with {:ok, email} <- require_email(conn) do
      # Force the fixed gateway self-url as the notify target — clients can
      # no longer drive SSRF via an arbitrary notify_url (Phase 0.3).
      body = Map.merge(params, %{"email" => email, "notify_url" => gateway_notify_url(email)})
      # Phase 8.10 — proxy_authenticated re-resolves the bearer, so call
      # the known-email variant directly to avoid a double resolver hit.
      proxy_with_email(conn, :matchmaking_service, :post, "/api/matchmaking/join", email, body)
    else
      _ -> unauthorized(conn)
    end
  end

  def matchmaking_leave(conn, params) do
    with {:ok, email} <- require_email(conn) do
      body = Map.put(params, "email", email)
      proxy_with_email(conn, :matchmaking_service, :post, "/api/matchmaking/leave", email, body)
    else
      _ -> unauthorized(conn)
    end
  end

  # ──────────────────────────── Social Endpoints ────────────────────────────
  # Social actions rely on X-Caller-Email injected from the verified Bearer
  # token — the client-supplied X-Caller-Email is never forwarded (see 0.1).

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

  # ─── Social proxy (injects X-Caller-Email from Bearer) ────────────────────

  defp proxy_social(conn, method, path, params) do
    url = Services.get_service_url(:social_service)
    full_url = url <> path

    # Phase 8.10 — go through the configurable AuthVerifier via require_email
    # so the social proxy is mockable too. Previously this called
    # AuthBridge.internal_headers/1, which bypassed the verifier indirection
    # and forced tests to spin up auth_service.
    with {:ok, email} <- require_email(conn) do
      headers = auth_headers_for_bearer(conn, email, content_type?: true)
      request_body = if method == :get, do: "", else: Jason.encode!(params)
      do_request(conn, method, full_url, request_body, headers)
    else
      _ -> unauthorized(conn)
    end
  end

  # ─── Authenticated proxy (used by user_service + matchmaking) ──────────────

  defp proxy_authenticated(conn, service, method, path, params) do
    with {:ok, email} <- require_email(conn) do
      proxy_with_email(conn, service, method, path, email, params)
    else
      _ -> unauthorized(conn)
    end
  end

  # Phase 8.10 — single auth-proxy helper used by both proxy_authenticated
  # (re-resolves email) and matchmaking_*/proxy_social (email already known)
  # so tests stubbing AuthVerifierMock see exactly one resolve per request.
  defp proxy_with_email(conn, service, method, path, email, params) do
    headers = auth_headers_for_bearer(conn, email, content_type?: true)
    proxy_request(conn, service, method, path, params, preset_headers: headers)
  end

  defp auth_headers_for_bearer(conn, email, opts) do
    base = [
      {"X-Caller-Email", email},
      {"X-Internal-Secret", AuthBridge.internal_secret()}
    ]

    base =
      case Keyword.get(opts, :content_type?) do
        true -> [{"Content-Type", "application/json"} | base]
        _ -> base
      end

    case AuthBridge.bearer_token(conn) do
      {:ok, token} -> [{"Authorization", "Bearer " <> token} | base]
      _ -> base
    end
  end

  defp require_email(conn) do
    with {:ok, token} <- AuthBridge.bearer_token(conn),
         # Phase 8.10 — resolve Bearer via the configurable AuthVerifier
         # behaviour so tests can stub this without booting auth_service.
         {:ok, email} <- auth_verifier().resolve_email(token) do
      {:ok, email}
    else
      _ -> {:error, :unauthorized}
    end
  end

  defp unauthorized(conn), do: render_error(conn, 401, "Not authenticated")

  defp gateway_notify_url(email) do
    self_url = Application.get_env(:api_gateway, :self_url) || "http://localhost:4000"
    "#{self_url}/api/internal/notify/#{email}"
  end

  # ──────────────────────────── Proxy Helper ───────────────────────────────

  defp proxy_request(conn, service, method, path, params, opts \\ []) do
    url = Services.get_service_url(service)
    full_url = url <> path

    base_headers = Keyword.get(opts, :preset_headers, [{"Content-Type", "application/json"}])

    headers =
      base_headers
      |> maybe_forward_auth(conn)

    request_body = if method == :get, do: "", else: Jason.encode!(params)

    do_request(conn, method, full_url, request_body, headers)
  end

  defp maybe_forward_auth(headers, conn) do
    case Plug.Conn.get_req_header(conn, "authorization") do
      ["Bearer " <> _ = auth | _] ->
        if Enum.any?(headers, fn {k, _} -> String.downcase(k) == "authorization" end) do
          headers
        else
          [{"Authorization", auth} | headers]
        end

      _ ->
        headers
    end
  end

  defp do_request(conn, method, full_url, request_body, headers) do
    # Phase 8.10 — go through the configurable HTTPClient behaviour so tests
    # can stub the downstream HTTP layer (see ApiGateway.HTTPClient). Real
    # traffic still uses HTTPoison — only the indirection has changed.
    case http_client().request(method, full_url, request_body, headers, @downstream_opts) do
      {:ok, %HTTPoison.Response{status_code: status, body: body}} ->
        conn
        |> put_status(status)
        |> json(decode_body(body))

      {:error, %HTTPoison.Error{reason: _reason}} ->
        # Phase 3.3 — uniform `%{error: "msg"}` envelope. The downstream
        # failure reason is logged (AuthBridge / downstream caller) but
        # not echoed to the client — the previous `%{error:..., reason:...}`
        # shape was one of the four inconsistent envelopes the frontend
        # couldn't parse.
        render_error(conn, 503, "Service unavailable")
    end
  end

  # Phase 8.10 — swappable HTTP implementation (HTTPoison by default;
  # Mox mock under :test). Defined late so it's easy to grep.
  defp http_client do
    Application.get_env(:api_gateway, :http_client, ApiGateway.HTTPClient.HTTPoison)
  end

  defp auth_verifier do
    Application.get_env(:api_gateway, :auth_verifier, ApiGateway.AuthVerifier.AuthBridge)
  end

  defp decode_body(""), do: %{}

  defp decode_body(body) do
    case Jason.decode(body) do
      {:ok, decoded} -> decoded
      {:error, _} -> %{raw_body: body}
    end
  end
end
