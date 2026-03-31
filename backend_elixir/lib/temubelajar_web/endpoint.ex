defmodule TemuBelajarWeb.Endpoint do
  use Phoenix.Endpoint, otp_app: :temubelajar

  # WebSocket untuk Phoenix Channels
  socket "/socket", TemuBelajarWeb.UserSocket,
    websocket: [timeout: 45_000],
    longpoll: false

  plug CORSPlug,
    origin: ["*"],
    methods: ["GET", "POST", "PUT", "DELETE", "OPTIONS"],
    headers: ["Authorization", "Content-Type", "Accept"]

  plug Plug.RequestId
  plug Plug.Telemetry, event_prefix: [:phoenix, :endpoint]

  plug Plug.Parsers,
    parsers: [:urlencoded, :multipart, :json],
    pass: ["*/*"],
    json_decoder: Phoenix.json_library()

  plug Plug.MethodOverride
  plug Plug.Head
  plug TemuBelajarWeb.Router
end
