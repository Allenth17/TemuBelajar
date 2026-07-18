defmodule SocialServiceWeb.Plugs.InternalAuth do
  @moduledoc """
  Service-to-service auth for social_service (Phase 0.1 / 0.17).

  The gateway derives the caller's email from the Bearer token and forwards
  it as X-Caller-Email along with the shared X-Internal-Secret HMAC. We:

    1. Reject any request whose X-Internal-Secret doesn't match.
    2. Assign conn.assigns.caller_email from the gateway-injected value.

  The X-Caller-Email header is therefore only trusted when accompanied by
  a valid internal secret — a client cannot forge it (CORS doesn't allow
  the header, and the bucket-strip lets us keep the old controller logic).
  """

  import Plug.Conn
  import Phoenix.Controller, only: [json: 2]

  def init(opts), do: opts

  def call(conn, _opts) do
    with [secret | _] <- get_req_header(conn, "x-internal-secret"),
         true <- valid_internal_secret?(secret),
         [email | _] <- get_req_header(conn, "x-caller-email"),
         true <- byte_size(email) > 0 do
      assign(conn, :caller_email, email)
    else
      _ ->
        conn
        |> put_status(:unauthorized)
        |> json(%{error: "Unauthorized"})
        |> halt()
    end
  end

  defp valid_internal_secret?(candidate) when is_binary(candidate) do
    expected =
      Application.get_env(:social_service, :internal_secret) ||
        "dev_internal_secret_replace_in_production"

    byte_size(candidate) == byte_size(expected) and
      :crypto.hash_equals(expected, candidate)
  end
end
