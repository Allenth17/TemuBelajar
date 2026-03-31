defmodule SignalingServiceWeb.ChannelCase do
  @moduledoc """
  ExUnit test case for Phoenix channel tests in the Signaling Service.

  Provides a connected socket with a pre-assigned email so tests can join
  channels without going through the real auth service HTTP call.
  """

  use ExUnit.CaseTemplate

  using do
    quote do
      import Phoenix.ChannelTest
      import SignalingServiceWeb.ChannelCase

      @endpoint SignalingServiceWeb.Endpoint
    end
  end

  setup _tags do
    # Ensure ETS table exists for tests
    case :ets.whereis(:signaling_peers) do
      :undefined ->
        :ets.new(:signaling_peers, [:named_table, :public, :bag])
      _ ->
        :ets.delete_all_objects(:signaling_peers)
    end

    # Build a socket with test email pre-assigned (bypasses HTTP auth)
    {:ok, socket} = Phoenix.ChannelTest.connect(
      SignalingServiceWeb.UserSocket,
      %{"token" => "test_bypass_token"},
      connect_info: %{}
    )

    # If connect rejects the test token, build socket directly
    socket = case socket do
      {:ok, s} -> s
      _ ->
        Phoenix.Socket.put_assigns(
          %Phoenix.Socket{
            endpoint: SignalingServiceWeb.Endpoint,
            pubsub_server: SignalingService.PubSub,
            handler: SignalingServiceWeb.UserSocket,
            transport: :test
          },
          email: "test@ui.ac.id"
        )
    end

    {:ok, socket: socket}
  end

  @doc "Build a test socket with a given email assigned directly (no auth)."
  def socket_with_email(email) do
    %Phoenix.Socket{
      endpoint: SignalingServiceWeb.Endpoint,
      pubsub_server: SignalingService.PubSub,
      handler: SignalingServiceWeb.UserSocket,
      transport: :test,
      assigns: %{email: email}
    }
  end
end
