defmodule SignalingServiceTest do
  use ExUnit.Case
  doctest SignalingService

  test "greets the world" do
    assert SignalingService.hello() == :world
  end
end
