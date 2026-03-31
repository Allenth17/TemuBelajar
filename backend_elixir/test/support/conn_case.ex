defmodule TemuBelajarWeb.ConnCase do
  use ExUnit.CaseTemplate

  using do
    quote do
      import Phoenix.ConnTest
      import Plug.Conn
      import TemuBelajarWeb.ConnCase

      @endpoint TemuBelajarWeb.Endpoint
    end
  end

  setup tags do
    TemuBelajar.DataCase.setup_sandbox(tags)
    {:ok, conn: Phoenix.ConnTest.build_conn()}
  end
end
