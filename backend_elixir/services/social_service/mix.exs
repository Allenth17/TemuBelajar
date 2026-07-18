defmodule SocialService.MixProject do
  use Mix.Project

  def project do
    [
      app: :social_service,
      version: "0.1.0",
      elixir: "~> 1.14",
      elixirc_paths: elixirc_paths(Mix.env()),
      start_permanent: Mix.env() == :prod,
      aliases: aliases(),
      deps: deps()
    ]
  end

  def application do
    [
      extra_applications: [:logger, :runtime_tools],
      mod: {SocialService.Application, []}
    ]
  end

  defp elixirc_paths(:test), do: ["lib", "test/support"]
  defp elixirc_paths(_), do: ["lib"]

  defp deps do
    [
      {:phoenix, "~> 1.7.18"},
      {:phoenix_ecto, "~> 4.4"},
      # Phase 8.12 — relax the patch-level pin to `~> 3.10` (covers 3.11)
      # and align bandit to the rest of the services at `~> 1.10`.
      {:ecto_sql, "~> 3.10"},
      {:postgrex, "~> 0.17.0"},
      {:jason, "~> 1.2"},
      {:cors_plug, "~> 3.0"},
      {:bandit, "~> 1.10"},
      {:httpoison, "~> 2.0"},
      {:telemetry_metrics, "~> 1.0"},
      {:telemetry_poller, "~> 1.0"}
    ]
  end

  defp aliases do
    [
      setup: ["deps.get", "ecto.setup"],
      "ecto.setup": ["ecto.create", "ecto.migrate"],
      "ecto.reset": ["ecto.drop", "ecto.setup"],
      test: ["ecto.create --quiet", "ecto.migrate --quiet", "test"]
    ]
  end
end
