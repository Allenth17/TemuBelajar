defmodule ApiGateway.AuthBridge do
  @moduledoc """
  Server-side identity derivation for the gateway.

  Responsibilities (Phase 0.1 / 0.3 / 0.6 / 0.17):
    1. Verify a client-supplied Bearer token against the auth_service
       `/api/verify-token` endpoint and return the canonical email.
    2. Provide a short-lived ETS cache so repeat lookups within the same
       second don't hammer the auth_service.
    3. Sign/verify an `X-Internal-Secret` HMAC shared with downstream
       services so they can trust gateway-injected headers (`X-Caller-Email`).

  Callers MUST treat the `X-Caller-Email` request header coming from a
  client as untrusted; only the value injected by this module (via
  `internal_headers/1`) is forwarded to downstream services.
  """

  use GenServer

  require Logger

  @cache_table :gateway_auth_cache
  @cache_ttl_ms 1_000
  # Tight timeouts for service-to-service HTTP — see 0.22 / 3.22.
  @http_opts [recv_timeout: 2_000, connect_timeout: 2_000]

  # ─── Public API ──────────────────────────────────────────────────────────────

  @doc """
  Resolve the canonical email for a Bearer token. Returns `{:ok, email}` or
  `{:error, reason}`. Result is cached for `@cache_ttl_ms` to amortize the
  HTTP round-trip across concurrent social requests.
  """
  def resolve_email(token) when is_binary(token) do
    case cache_get(token) do
      {:ok, _} = ok ->
        ok

      :miss ->
        case verify_with_auth_service(token) do
          {:ok, email} ->
            cache_put(token, email)
            {:ok, email}

          {:error, _} = err ->
            err
        end
    end
  end

  def resolve_email(_), do: {:error, :invalid_token}

  @doc """
  Extract Bearer token from a `%Plug.Conn{}`'s `authorization` header.
  """
  def bearer_token(conn) do
    case Plug.Conn.get_req_header(conn, "authorization") do
      ["Bearer " <> token | _] when byte_size(token) > 0 -> {:ok, token}
      _ -> :error
    end
  end

  @doc """
  Verify a Bearer token and return gateway-internal headers to inject into
  the downstream request. The client's `X-Caller-Email` (if any) is dropped —
  servers only trust the gateway-injected value.
  """
  def internal_headers(conn) do
    with {:ok, token} <- bearer_token(conn),
         {:ok, email} <- resolve_email(token) do
      headers = [
        {"Authorization", "Bearer " <> token},
        {"X-Caller-Email", email},
        {"X-Internal-Secret", internal_secret()}
      ]

      {:ok, headers}
    else
      _ -> {:error, :unauthorized}
    end
  end

  @doc """
  HMAC secret shared across all services for `X-Internal-Secret` validation.
  Read from `INTERNAL_SECRET` env (falls back to a dev value so local runs
  still work; `runtime.exs` raises in :prod when unset).
  """
  def internal_secret do
    Application.get_env(:api_gateway, :internal_secret) ||
      "dev_internal_secret_replace_in_production"
  end

  @doc """
  Constant-time-ish comparison used by the CS plug in downstream services.
  """
  def valid_internal_secret?(candidate) when is_binary(candidate) do
    expected = internal_secret()

    byte_size(candidate) == byte_size(expected) and
      :crypto.hash_equals(expected, candidate)
  end

  # ─── GenServer plumbing & cache ──────────────────────────────────────────────

  def start_link(_opts), do: GenServer.start_link(__MODULE__, :ok, name: __MODULE__)

  @impl true
  def init(:ok) do
    :ets.new(@cache_table, [:named_table, :public, :set, read_concurrency: true])
    schedule_sweep()
    {:ok, %{}}
  end

  @impl true
  def handle_info(:sweep, state) do
    now = System.monotonic_time(:millisecond)

    :ets.tab2list(@cache_table)
    |> Enum.each(fn {token, {_email, exp}} ->
      if exp <= now, do: :ets.delete(@cache_table, token)
    end)

    schedule_sweep()
    {:noreply, state}
  end

  defp schedule_sweep, do: Process.send_after(self(), :sweep, @cache_ttl_ms * 2)

  defp cache_get(token) do
    case :ets.lookup(@cache_table, token) do
      [{^token, {email, exp}}] ->
        if exp > System.monotonic_time(:millisecond),
          do: {:ok, email},
          else: :miss

      _ ->
        :miss
    end
  end

  defp cache_put(token, email) do
    exp = System.monotonic_time(:millisecond) + @cache_ttl_ms
    :ets.insert(@cache_table, {token, {email, exp}})
  end

  # ─── auth_service HTTP call ──────────────────────────────────────────────────

  defp verify_with_auth_service(token) do
    url = ApiGateway.Services.get_service_url(:auth_service)

    headers = [{"X-Internal-Secret", internal_secret()}]

    case HTTPoison.get("#{url}/api/verify-token?token=#{token}", headers, @http_opts) do
      {:ok, %HTTPoison.Response{status_code: 200, body: body}} ->
        case Jason.decode(body) do
          {:ok, %{"valid" => true, "email" => email}} when is_binary(email) ->
            {:ok, email}

          _ ->
            {:error, :invalid_token}
        end

      {:ok, %HTTPoison.Response{status_code: status}} ->
        Logger.debug("[AuthBridge] verify-token rejected status=#{status}")
        {:error, :invalid_token}

      {:error, %HTTPoison.Error{reason: reason}} ->
        Logger.warn("[AuthBridge] verify-token unreachable: #{inspect(reason)}")
        {:error, :auth_unavailable}
    end
  end
end
