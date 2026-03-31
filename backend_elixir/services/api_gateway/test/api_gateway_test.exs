defmodule ApiGatewayTest do
  use ExUnit.Case
  doctest ApiGateway

  test "greets the world" do
    assert ApiGateway.hello() == :world
  end
end
