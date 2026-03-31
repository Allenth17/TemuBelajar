defmodule UUID do
  @doc "Generate UUID v4"
  def uuid4 do
    <<a::32, b::16, _::4, c::12, _::2, d::30, e::48>> = :crypto.strong_rand_bytes(16)
    <<a::32, b::16, 4::4, c::12, 2::2, d::30, e::48>>
    |> Base.encode16(case: :lower)
    |> format_uuid()
  end

  defp format_uuid(<<a::binary-8, b::binary-4, c::binary-4, d::binary-4, e::binary-12>>) do
    "#{a}-#{b}-#{c}-#{d}-#{e}"
  end
end
