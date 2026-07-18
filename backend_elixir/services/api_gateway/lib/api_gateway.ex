defmodule ApiGateway do
  @moduledoc """
  API Gateway root module. The gateway is a pure HTTP/WebSocket proxy
  with no domain logic of its own — see `ApiGatewayWeb.Router` and
  `ApiGatewayWeb.GatewayController` for the routing surface, and
  `ApiGateway.Services` / `ApiGateway.AuthBridge` for downstream plumbing.
  """

  # Phase 8.11 — removed the leftover Phoenix generator `hello/0` doctest
  # stub. It exercised no gateway behaviour and the only caller was the
  # boilerplate test in test/api_gateway_test.exs, which was deleted with it.
end
