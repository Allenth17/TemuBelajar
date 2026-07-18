defmodule ApiGateway.HTTPClient do
  @moduledoc """
  Phase 8.10 — a thin behaviour around HTTPoison so the gateway's proxy
  path can be stubbed in tests. Without this indirection the controller
  hard-codes `HTTPoison.request/5`, and the only way to assert on a
  specific upstream response is to actually run all upstream services —
  which the previous router_test didn't, hence its
  `assert conn.status in [200,201,400,401,404,422,503]` swallow-everything
  pattern that gave false green on a 503.

  The default impl (`ApiGateway.HTTPClient.HTTPoison`) delegates straight
  to `HTTPoison.request/5`; tests swap in a Mox mock by setting
  `Application.put_env(:api_gateway, :http_client, ApiGateway.HTTPClientMock)`.
  """

  @type method :: :get | :post | :put | :delete | :patch | :head | :options
  @type headers :: [{String.t(), String.t()}]
  @type opts :: keyword()
  @type response ::
          {:ok, %{status_code: pos_integer(), body: binary()}} | {:error, %{reason: any()}}
  @type request_result :: {:ok, %HTTPoison.Response{}} | {:error, %HTTPoison.Error{}}

  @callback request(method(), String.t(), any(), headers(), opts()) :: request_result()
end
