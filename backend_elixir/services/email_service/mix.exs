defmodule EmailService.MixProject do
  use Mix.Project

  def project do
    [
      app: :email_service,
      version: "0.1.0",
      elixir: "~> 1.14",
      elixirc_paths: elixirc_paths(Mix.env()),
      start_permanent: Mix.env() == :prod,
      deps: deps()
    ]
  end

  # Specifies which paths to compile per environment.
  defp elixirc_paths(:test), do: ["lib", "test/support"]
  defp elixirc_paths(_), do: ["lib"]

  # Run "mix help compile.app" to learn about applications.
  def application do
    [
      extra_applications: [:logger, :crypto],
      mod: {EmailService.Application, []}
    ]
  end

  # Run "mix help deps" to learn about dependencies.
  defp deps do
    [
      {:phoenix, "~> 1.7.21"},
      {:jason, "~> 1.4"},
      {:bandit, "~> 1.10"},
      {:cors_plug, "~> 3.0"},
      # Email sending via SMTP
      {:swoosh, "~> 1.5"},
      {:gen_smtp, "~> 1.0"},
      {:hackney, "~> 1.9"},
      {:telemetry_metrics, "~> 1.0"},
      {:telemetry_poller, "~> 1.0"},
      {:gettext, "~> 0.20"}
    ]
  end
end
