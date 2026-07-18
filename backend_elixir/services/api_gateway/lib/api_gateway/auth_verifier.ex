defmodule ApiGateway.AuthVerifier do
  @moduledoc """
  Phase 8.10 — behaviour around identity resolution so the gateway can stub
  Bearer→email lookup in tests without spinning up the auth_service and
  its Postgres. The default impl (`ApiGateway.AuthVerifier.AuthBridge`)
  delegates to `ApiGateway.AuthBridge.resolve_email/1`, preserving the
  live behaviour (HTTP call to auth_service + ETS cache).

  Tests swap in a Mox mock by setting
  `Application.put_env(:api_gateway, :auth_verifier, ApiGateway.AuthVerifierMock)`.
  """

  @callback resolve_email(token :: binary()) ::
              {:ok, binary()} | {:error, :invalid_token | :auth_unavailable}
end
