defmodule SocialServiceWeb.ConnCase do
  @moduledoc """
  ConnCase for SocialServiceWeb HTTP-level tests.

  Mirrors the internal cross-service auth chain: tests pass
  `X-Internal-Secret` + `X-Caller-Email` so the InternalAuth plug accepts the
  request (same headers gateway / matchmaking_service forward in prod).
  """

  use ExUnit.CaseTemplate

  # Phase 8.10 — import Plug.Conn in the module body so helper functions
  # defined here (e.g. internal_conn/3) can call put_req_header/3. Importing
  # it only in `using do ... end` exposes those functions to the test
  # modules, not to this module's own functions.
  import Plug.Conn

  using do
    quote do
      import Phoenix.ConnTest
      import Plug.Conn
      import SocialServiceWeb.ConnCase

      @endpoint SocialServiceWeb.Endpoint
    end
  end

  setup tags do
    pid = Ecto.Adapters.SQL.Sandbox.start_owner!(SocialService.Repo, shared: not tags[:async])
    on_exit(fn -> Ecto.Adapters.SQL.Sandbox.stop_owner(pid) end)

    {:ok, conn: Phoenix.ConnTest.build_conn()}
  end

  @doc """
  Adds the internal-service auth headers a cross-service caller (gateway or
  matchmaking_service) forwards. `caller_email` is the user the request is
  about — for user-proxied routes it's the verified Bearer identity, for
  `/internal/blocked-by` it's the caller the matchmaking service is asking
  about.
  """
  def internal_conn(conn, caller_email, secret \\ dev_secret()) do
    conn
    |> put_req_header("x-internal-secret", secret)
    |> put_req_header("x-caller-email", caller_email)
  end

  defp dev_secret, do: "dev_internal_secret_replace_in_production"
end
