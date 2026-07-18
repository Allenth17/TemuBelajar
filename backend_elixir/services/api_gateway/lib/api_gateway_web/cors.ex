defmodule ApiGatewayWeb.CORS do
  @moduledoc """
  Phase 8.16 — runtime-resolved CORS origin allowlist.

  Previously the gateway served `Access-Control-Allow-Origin: *` to every
  caller. With the Bearer-token auth model this was tolerable but wide:
  any origin could probe the proxy surface. Origins are now an allowlist
  stored under `Application.get_env(:api_gateway, :cors_origins)`:

    * In :prod the default is `["https://temubelajar.id"]` (set in
      runtime.exs). `CORS_ALLOWED_ORIGINS=""` rejects all cross-origin
      requests (empty allowlist) for deployments that only do server-to-server.
    * In :dev / :test the default is `["*"]` so local Android/iOS emulators
      and other front-ends work without extra config.

  `CORSPlug` accepts a 0-arity function for `origin:` and evaluates it on
  every request, so the allowlist read here is re-evaluated at runtime
  (env flips at boot take effect on the first request).
  """

  @prod_default ["https://temubelajar.id"]

  @spec origins() :: [String.t()]
  def origins do
    case Application.get_env(:api_gateway, :cors_origins) do
      list when is_list(list) ->
        list

      nil ->
        # No explicit config (e.g. running outside the standard config chain).
        # Re-resolve against the env var with safe prod-style defaults.
        raw = System.get_env("CORS_ALLOWED_ORIGINS")

        cond do
          raw != nil -> parse(raw)
          prod_release?() -> @prod_default
          true -> ["*"]
        end
    end
  end

  defp parse(raw) do
    raw
    |> String.split(",", trim: true)
    |> Enum.map(&String.trim/1)
    |> Enum.reject(&(&1 == ""))
  end

  # Releases set `RELEASE_NAME`/`RELEASE_VSN`. Better heuristic than `MIX_ENV`
  # which is absent inside a release boot.
  defp prod_release?, do: System.get_env("RELEASE_NAME") != nil
end
