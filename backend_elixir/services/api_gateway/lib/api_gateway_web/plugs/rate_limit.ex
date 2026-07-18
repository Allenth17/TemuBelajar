defmodule ApiGatewayWeb.Plugs.RateLimit do
  @moduledoc """
  Phase 3.20 — per-IP, per-route sliding-window rate limiter for the
  auth-issuing endpoints (`/api/login`, `/api/verify-otp`, `/api/register`).
  Backed by a single named ETS table, so the limiter state is in-memory and
  local to this gateway node (a clustered gateway would need a shared store
  such as Hammer + Redis; for the single-node dev/qa target this is fine).

  Default: 30 requests / minute per (IP, route). When exceeded the plug
  halts the conn with a `429 Too Many Requests` and a
  `%{error: "Terlalu banyak permintaan, coba lagi nanti"}` body so the
  frontend's existing string-handling code path surfaces it.

  The window is implemented as a fixed 60-second bucket keyed on the
  client IP (from `remote_ip`, falling back to the `X-Forwarded-For`
  header when present) and the matched route path pattern. Each row also
  carries an `expires_at` monotonic timestamp so a lazy sweeper culls
  stale buckets and the table can't grow unbounded.
  """

  @behaviour Plug

  import Plug.Conn
  import Phoenix.Controller, only: [json: 2]

  @table :gateway_rate_limit
  @window_ms 60_000
  @default_limit 30
  @default_routes [
    "/api/login",
    "/api/verify-otp",
    "/api/register"
  ]

  # ─── Plug plumbing ───────────────────────────────────────────────────────────

  @impl true
  def init(opts) do
    %{
      limit: Keyword.get(opts, :limit, @default_limit),
      routes: Keyword.get(opts, :routes, @default_routes) |> Enum.map(&normalize_route/1)
    }
  end

  @impl true
  def call(conn, %{routes: routes} = opts) do
    ensure_ets()

    case match_route(conn, routes) do
      nil ->
        conn

      route_key ->
        ip = client_ip(conn)

        if allowed?(ip, route_key, opts.limit) do
          conn
        else
          conn
          |> put_status(429)
          |> json(%{error: "Terlalu banyak permintaan, coba lagi nanti"})
          |> halt()
        end
    end
  end

  # ─── private ─────────────────────────────────────────────────────────────────

  # The configured routes are either verified route sigils (`~p"/api/login"`),
  # binaries, or `{"METHOD", "/path"}` tuples. Normalize to plain binary
  # paths — the rate-limit table keys on the path, not on the method, so a
  # flood of POSTs to /api/login and a stray GET to /api/login both count
  # against the same window for that IP.
  defp normalize_route({_, path}) when is_binary(path), do: path
  defp normalize_route(path) when is_binary(path), do: path

  defp match_route(conn, routes) do
    # conn.request_path is the raw matched path (e.g. "/api/login"); for
    # interpolated routes like "/api/user/:email" we wouldn't match here,
    # but the rate-limited routes above are all static paths.
    path = conn.request_path || "/" <> Enum.join(conn.path_info, "/")

    if path in routes, do: path, else: nil
  end

  defp client_ip(conn) do
    case get_req_header(conn, "x-forwarded-for") do
      [forwarded | _] when is_binary(forwarded) and byte_size(forwarded) > 0 ->
        forwarded |> String.split(",") |> List.first() |> String.trim() |> to_string()

      _ ->
        case conn.remote_ip do
          nil -> "unknown"
          ip -> ip |> :inet_parse.ntoa() |> to_string()
        end
    end
  end

  # Sliding-window-ish counter: a row keyed by {ip, route} holds
  # `{count, window_start}`. If the window has aged out, we reset it.
  # Each request bumps the count; once it exceeds `limit` we reject.
  defp allowed?(ip, route, limit) do
    key = {ip, route}
    now = System.monotonic_time(:millisecond)

    case :ets.lookup(@table, key) do
      [{^key, {count, window_start}}] ->
        if now - window_start >= @window_ms do
          # Window rolled over — start fresh.
          :ets.insert(@table, {key, {1, now}})
          maybe_sweep()
          true
        else
          new_count = count + 1
          :ets.insert(@table, {key, {new_count, window_start}})
          new_count <= limit
        end

      [] ->
        :ets.insert(@table, {key, {1, now}})
        true
    end
  end

  # Opportunistic sweep — drop expired buckets on roughly 1% of inserts so
  # the table doesn't grow unbounded. Cheap and avoids a background timer.
  defp maybe_sweep do
    if :rand.uniform(100) == 1 do
      sweep()
    end
  end

  defp sweep do
    now = System.monotonic_time(:millisecond)

    :ets.tab2list(@table)
    |> Enum.each(fn {key, {_count, window_start}} ->
      if now - window_start >= @window_ms, do: :ets.delete(@table, key)
    end)
  rescue
    # Table may have been concurrently dropped by a host restart — ignore.
    _ -> :ok
  end

  # The Application supervisor creates the table before the endpoint comes
  # up, but tests / `iex -S mix` may bypass `Application.start/2` so we
  # ensure it here too. Cheap `:ets.whereis` check; first-call race on
  # `:ets.new/2` is harmless because `:named_table` is unique — the second
  # caller crashes and the supervisor catches it.
  defp ensure_ets do
    case :ets.whereis(@table) do
      :undefined -> :ets.new(@table, [:named_table, :public, :set, read_concurrency: true])
      _ -> :ok
    end
  end
end
