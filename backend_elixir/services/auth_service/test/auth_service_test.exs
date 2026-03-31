defmodule AuthServiceTest do
  use ExUnit.Case
  doctest AuthService

  test "greets the world" do
    assert AuthService.hello() == :world
  end
end
