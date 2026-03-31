defmodule AuthService.MixProject do
  use Mix.Project

  def project do
    [
      app: :auth_service,
      version: "0.1.0",
      elixir: "~> 1.14",
      elixirc_paths: elixirc_paths(Mix.env()),
      start_permanent: Mix.env() == :prod,
      aliases: aliases(),
      deps: deps()
    ]
  end

  # Run "mix help compile.app" to learn about applications.
  def application do
    [
      extra_applications: [:logger, :runtime_tools, :crypto],
      mod: {AuthService.Application, []}
    ]
  end

  defp elixirc_paths(:test), do: ["lib", "test/support"]
  defp elixirc_paths(_), do: ["lib"]

  # Run "mix help deps" to learn about dependencies.
  defp deps do
    [
      # Web framework — uses Bandit (not Cowboy) for lower memory usage
      {:phoenix, "~> 1.7.18"},
      {:phoenix_ecto, "~> 4.4"},
      {:ecto_sql, "~> 3.10.0"},
      {:postgrex, "~> 0.17.0"},
      {:jason, "~> 1.2"},
      {:cors_plug, "~> 3.0"},
      {:bandit, "~> 1.5"},

      # Password hashing — bcrypt_elixir already includes comeonin behaviour
      {:bcrypt_elixir, "~> 3.0"},

      # HTTP client for inter-service calls
      {:httpoison, "~> 2.0"},

      # Email sending
      {:swoosh, "~> 1.5"},
      {:gen_smtp, "~> 1.0"},
      {:finch, "~> 0.13"},

      # Telemetry
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
