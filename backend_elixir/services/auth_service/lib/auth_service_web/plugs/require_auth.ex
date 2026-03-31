defmodule AuthServiceWeb.Plugs.RequireAuth do
  import Plug.Conn
  import Phoenix.Controller, only: [json: 2]

  def init(opts), do: opts

  def call(conn, _opts) do
    token =
      case get_req_header(conn, "authorization") do
        ["Bearer " <> t] -> t
        _ -> nil
      end

    case token && AuthService.Accounts.get_email_by_token(token) do
      nil ->
        conn
        |> put_status(:unauthorized)
        |> json(%{error: "Token tidak valid atau kadaluarsa"})
        |> halt()

      email ->
        assign(conn, :current_email, email)
    end
  end
end
