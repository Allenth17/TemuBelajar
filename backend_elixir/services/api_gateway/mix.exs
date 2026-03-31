defmodule ApiGateway.MixProject do
  use Mix.Project

  def project do
    [
      app: :api_gateway,
      version: "0.1.0",
      elixir: "~> 1.14",
      elixirc_paths: elixirc_paths(Mix.env()),
      start_permanent: Mix.env() == :prod,
      aliases: aliases(),
      deps: deps()
    ]
  end

  # Specifies which paths to compile per environment.
  defp elixirc_paths(:test), do: ["lib", "test/support"]
  defp elixirc_paths(_), do: ["lib"]

  # Run "mix help compile.app" to learn about applications.
  def application do
    [
      extra_applications: [:logger, :runtime_tools, :crypto],
      mod: {ApiGateway.Application, []}
    ]
  end

  # Run "mix help deps" to learn about dependencies.
  defp deps do
    [
      # Web framework — uses Bandit (not Cowboy) for lower memory usage
      {:phoenix, "~> 1.7.21"},
      {:jason, "~> 1.4"},
      {:bandit, "~> 1.10"},
      {:cors_plug, "~> 3.0"},

      # HTTP client for proxying requests to microservices
      {:httpoison, "~> 2.0"},

      {:telemetry_metrics, "~> 1.0"},
      {:telemetry_poller, "~> 1.0"},
      {:gettext, "~> 0.20"}
    ]
  end

  defp aliases do
    [
      test: ["test"]
    ]
  end
end
