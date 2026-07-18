defmodule UserServiceWeb.Plugs.InternalAuth do
  @moduledoc """
  Service-to-service auth + caller-identity enforcement for user_service
  (Phase 0.4 / 0.17).

  The gateway derives the caller's email from the Bearer and forwards it
  to us along with `X-Internal-Secret`. We:

    1. Reject any request not coming from the gateway (mismatched secret).
    2. Assign `conn.assigns.caller_email` so controllers can authorize
       ownership (PUT /api/user/:email — caller must equal :email, list_users
       requires admin role).
  """

  import Plug.Conn
  import Phoenix.Controller, only: [json: 2]

  @internal_secret_key "x-internal-secret"
  @caller_email_key "x-caller-email"

  def init(opts), do: opts

  def call(conn, _opts) do
    with [secret | _] <- get_req_header(conn, @internal_secret_key),
         true <- valid_internal_secret?(secret),
         [email | _] <- get_req_header(conn, @caller_email_key),
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
      Application.get_env(:user_service, :internal_secret) ||
        "dev_internal_secret_replace_in_production"

    byte_size(candidate) == byte_size(expected) and
      :crypto.hash_equals(expected, candidate)
  end
end
