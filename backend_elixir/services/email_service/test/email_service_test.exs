defmodule EmailServiceTest do
  use ExUnit.Case
  doctest EmailService

  test "greets the world" do
    assert EmailService.hello() == :world
  end
end
