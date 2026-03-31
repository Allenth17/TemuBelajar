defmodule ApiGatewayWeb.Telemetry do
  use Supervisor
  import Telemetry.Metrics

  def start_link(arg) do
    children = [
      # Telemetry poller will execute the given period measurements
      # every 10_000ms. Learn more here: https://hexdocs.pm/telemetry_metrics
      {:telemetry_poller, measurements: periodic_measurements(), period: 10_000}
    ]

    Supervisor.start_link(children, strategy: :one_for_one, name: __MODULE__)
  end

  def metrics do
    [
      # Phoenix Metrics
      summary("phoenix.endpoint.stop.duration",
        unit: {:native, :millisecond},
        description: "Phoenix endpoint stop duration"
      ),
      summary("phoenix.router_dispatch.stop.duration",
        unit: {:native, :millisecond},
        description: "Phoenix router dispatch stop duration"
      ),
      counter("phoenix.socket_connected.count",
        description: "The total number of socket connections"
      ),
      counter("phoenix.channel_joined.count",
        description: "The total number of channels joined"
      ),

      # VM Metrics
      summary("vm.memory.total", unit: {:native, :kilobyte}),
      summary("vm.total_run_queue_lengths.total", unit: {:native, :millisecond}),
      summary("vm.total_run_queue_lengths.cpu", unit: {:native, :millisecond}),
      summary("vm.total_run_queue_lengths.io", unit: {:native, :millisecond})
    ]
  end

  defp periodic_measurements do
    [
      # A module, function and arguments to be invoked periodically.
      # This function must call :telemetry.execute/3 and a metric must be added above.
      # The first argument is the event name to be dispatched, the second is a map of
      # metadata, the third is the measurement. The measurement value is expected to be in ms.
      {__MODULE__, :dispatch_event, []}
    ]
  end

  def dispatch_event do
    :telemetry.execute([:api_gateway, :dispatch], %{system_time: System.system_time(:millisecond)}, %{})
  end
end
