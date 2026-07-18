defmodule SocialService.DataCase do
  @moduledoc """
  Plain DB test case for SocialService — used by tests that exercise the
  Social context directly without going through the HTTP pipeline.
  """

  use ExUnit.CaseTemplate

  using do
    quote do
      alias SocialService.Repo
      alias SocialService.Social.{Follow, FriendRequest, Block, Report}

      import Ecto
      import Ecto.Changeset
      import Ecto.Query
      import SocialService.DataCase
    end
  end

  setup tags do
    pid = Ecto.Adapters.SQL.Sandbox.start_owner!(SocialService.Repo, shared: not tags[:async])
    on_exit(fn -> Ecto.Adapters.SQL.Sandbox.stop_owner(pid) end)
    :ok
  end
end
