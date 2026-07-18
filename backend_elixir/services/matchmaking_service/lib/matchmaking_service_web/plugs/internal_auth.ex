defmodule MatchmakingServiceWeb.Plugs.InternalAuth do
  @moduledoc """
  Rejects unauthenticated POSTs to matchmaking internal endpoints
  (verify-pair, end-pair) from non-services.

  The gateway / signaling_service forwards `X-Internal-Secret` set to the
  shared HMAC. Unauthenticated external callers are rejected.
  """

  import Plug.Conn
  import Phoenix.Controller, only: [json: 2]

  def init(opts), do: opts

  def call(conn, _opts) do
    with [secret | _] <- get_req_header(conn, "x-internal-secret"),
         true <- valid_internal_secret?(secret) do
      conn
    else
      _ ->
        conn
        |> put_status(:unauthorized)
        |> json(%{error: "Internal service secret missing or invalid"})
        |> halt()
    end
  end

  defp valid_internal_secret?(candidate) when is_binary(candidate) do
    expected =
      Application.get_env(:matchmaking_service, :internal_secret) ||
        "dev_internal_secret_replace_in_production"

    byte_size(candidate) == byte_size(expected) and
      :crypto.hash_equals(expected, candidate)
  end
end
