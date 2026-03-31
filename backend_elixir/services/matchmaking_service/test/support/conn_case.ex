defmodule MatchmakingServiceWeb.ConnCase do
  @moduledoc """
  Test case template for MatchmakingService controller tests.
  No database — matchmaking uses ETS only.
  """

  use ExUnit.CaseTemplate

  using do
    quote do
      @endpoint MatchmakingServiceWeb.Endpoint

      use Phoenix.ConnTest
      import Plug.Conn
      import MatchmakingServiceWeb.ConnCase
    end
  end

  setup _tags do
    # Reset ETS tables before each test to ensure isolation
    if Process.whereis(MatchmakingService.MatchmakingServer) do
      MatchmakingService.MatchmakingServer.reset()
    end

    {:ok, conn: Phoenix.ConnTest.build_conn()}
  end
end
