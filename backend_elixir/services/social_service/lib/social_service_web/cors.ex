defmodule SocialServiceWeb.CORS do
  @moduledoc """
  Phase 8.16 — runtime-resolved CORS origin allowlist for the social_service.

  Social endpoints are only ever reached via the API gateway (which injects
  `X-Caller-Email` from a verified Bearer). The browser never directly calls
  the social service in production, so the prod default is the single client
  origin `https://temubelajar.id`. In dev/test we keep `["*"]` so local
  Postman / bare front-end calls keep working without an env override.

  Driven by `Application.get_env(:social_service, :cors_origins)` with env
  fallback to `CORS_ALLOWED_ORIGINS` (see config/*.exs).
  """

  @prod_default ["https://temubelajar.id"]

  @spec origins() :: [String.t()]
  def origins do
    case Application.get_env(:social_service, :cors_origins) do
      list when is_list(list) ->
        list

      nil ->
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

  defp prod_release?, do: System.get_env("RELEASE_NAME") != nil
end
