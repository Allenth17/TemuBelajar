defmodule ApiGateway.AuthVerifier.AuthBridge do
  @moduledoc """
  Phase 8.10 — default `ApiGateway.AuthVerifier` impl. Delegates straight to
  `ApiGateway.AuthBridge.resolve_email/1` (HTTP call to auth_service,
  ETS-cached), preserving the live behaviour of the authenticated proxy
  paths.
  """

  @behaviour ApiGateway.AuthVerifier

  @impl true
  def resolve_email(token) do
    ApiGateway.AuthBridge.resolve_email(token)
  end
end
