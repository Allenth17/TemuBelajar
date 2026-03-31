defmodule ApiGatewayWeb.ErrorJSON do
  @moduledoc """
  This module is invoked when your API encounters a
  client error or there is a rendering error.
  """

  # If you want to customize a particular status code,
  # you may add your own clauses, such as:
  #
  # def render("500.html", _assigns) do
  #   "Internal Server Error"
  # end
  #
  # def render("404.html", _assigns) do
  #   "Not Found"
  # end
  #
  def render(template, _assigns) do
    %{errors: %{detail: Phoenix.Controller.status_message_from_template(template)}}
  end
end
