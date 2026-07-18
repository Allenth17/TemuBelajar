defmodule EmailServiceWeb.Plugs.InternalAuth do
  @moduledoc """
  Rejects any unauthenticated POST to /api/send-otp. Only auth_service
  (which knows the freshly generated OTP) is allowed, validated via the
  shared `X-Internal-Secret` HMAC (Phase 0.5 / 0.17).

  Phase 0.5 risk: previously /api/send-otp was an open email relay —
  anyone could POST an arbitrary email + arbitrary OTP and trigger the
  Gmail SMTP relay.
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
      Application.get_env(:email_service, :internal_secret) ||
        "dev_internal_secret_replace_in_production"

    byte_size(candidate) == byte_size(expected) and
      :crypto.hash_equals(expected, candidate)
  end
end
