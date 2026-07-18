defmodule ApiGateway.HTTPClient.HTTPoison do
  @moduledoc """
  Phase 8.10 — default `ApiGateway.HTTPClient` impl. A passthrough to the
  real `HTTPoison.request/5` so production code is unchanged byte-for-byte
  with the previous direct call site.

  Phase 3.19 — adds bounded retry (max 3 attempts, exponential backoff
  capped at 5s) on transient 5xx upstream responses and HTTPoison transport
  errors. Retries trigger on:
    • HTTP 502 / 503 / 504 (transient gateway-unavailable / timeout)
    • `{:error, %HTTPoison.Error{}}` (connection refused, timeout, …)

  Non-retriable cases (2xx/4xx and 500 from a deterministic upstream bug)
  are surfaced immediately so the controller can render the uniform
  `%{error: ...}` envelope.
  """

  @behaviour ApiGateway.HTTPClient

  @max_attempts 3
  @backoff_cap_ms 5_000
  # Transient 5xx status codes that warrant a retry; 500 is deliberately
  # excluded — an upstream server-side crash is rarely transient and
  # retrying just doubles the load. 502/503/504 are the typical "within
  # one retry the upstream likely recovered" codes.
  @retriable_5xx_statuses [502, 503, 504]

  @impl true
  def request(method, url, body, headers, opts) do
    do_attempt(1, fn -> HTTPoison.request(method, url, body, headers, opts) end)
  end

  # ─── private ────────────────────────────────────────────────────────────────

  # Final attempt — don't retry (call materializes the last result).
  defp do_attempt(@max_attempts, call), do: call.()

  defp do_attempt(n, call) do
    case call.() do
      {:ok, %HTTPoison.Response{status_code: status}} when status in @retriable_5xx_statuses ->
        wait_exp(n)
        # If this was the last attempt, the recursive call returns the
        # underlying 5xx response (clause 1 above) and the controller
        # renders the uniform `%{error: ...}` envelope.
        do_attempt(n + 1, call)

      {:error, %HTTPoison.Error{}} ->
        wait_exp(n)
        do_attempt(n + 1, call)

      other ->
        # Deterministic response (2xx/4xx/500 or other) — pass through.
        other
    end
  end

  # Exponential backoff with a 5s cap: 250ms → 1s → 5s, then 5s onwards.
  defp wait_exp(n) when n >= 1 do
    delay = min(Integer.pow(2, n - 1) * 250, @backoff_cap_ms)
    Process.sleep(delay)
  end
end
