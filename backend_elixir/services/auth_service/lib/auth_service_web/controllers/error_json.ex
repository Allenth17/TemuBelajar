defmodule AuthServiceWeb.ErrorJSON do
  def render("404.json", _assigns), do: %{error: "Not Found"}
  def render("400.json", _assigns), do: %{error: "Bad Request"}
  def render("401.json", _assigns), do: %{error: "Unauthorized"}
  def render("403.json", _assigns), do: %{error: "Forbidden"}
  def render("422.json", %{changeset: changeset}) do
    %{errors: Ecto.Changeset.traverse_errors(changeset, &translate_error/1)}
  end
  def render(template, _assigns) do
    %{error: Phoenix.Controller.status_message_from_template(template)}
  end

  defp translate_error({msg, opts}) do
    Enum.reduce(opts, msg, fn {key, value}, acc ->
      String.replace(acc, "%{#{key}}", to_string(value))
    end)
  end
end
