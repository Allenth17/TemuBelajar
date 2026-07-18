defmodule ApiGatewayWeb.ConnCase do
  @moduledoc """
  This module defines the test case to be used by
  tests that require setting up a connection.

  Such tests rely on `Phoenix.ConnTest` and also
  import other functionality to make it easier to
  build common data structures and query the data layer.

  Finally, if the test case interacts with the database,
  we enable the SQL sandbox, so changes done to the database
  are reverted at the end of every test. If you are using
  PostgreSQL, you can even run database tests asynchronously
  by setting `use ApiGatewayWeb.ConnCase, async: true`, although
  this option is not recommended for other databases.
  """

  use ExUnit.CaseTemplate

  alias ApiGateway.{HTTPClientMock, AuthVerifierMock}

  using do
    quote do
      # The default endpoint for testing
      @endpoint ApiGatewayWeb.Endpoint

      import Plug.Conn
      import Phoenix.ConnTest
      import ApiGatewayWeb.ConnCase
    end
  end

  setup _tags do
    # Phase 8.10 — wire the controller's swappable deps to Mox mocks so the
    # router tests assert on specific upstream responses instead of
    # swallowing [200,201,400,401,404,422,503].
    Application.put_env(:api_gateway, :http_client, HTTPClientMock)
    Application.put_env(:api_gateway, :auth_verifier, AuthVerifierMock)

    Mox.verify_on_exit!()

    {:ok, conn: Phoenix.ConnTest.build_conn()}
  end
end
