defmodule UserServiceTest do
  use ExUnit.Case
  doctest UserService

  test "greets the world" do
    assert UserService.hello() == :world
  end
end
