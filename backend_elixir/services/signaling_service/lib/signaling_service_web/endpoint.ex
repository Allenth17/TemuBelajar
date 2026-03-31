defmodule SignalingServiceWeb.Endpoint do
  use Phoenix.Endpoint, otp_app: :signaling_service

  socket "/socket", SignalingServiceWeb.UserSocket,
    websocket: true,
    longpoll: false

  plug CORSPlug,
    origin: "*",
    methods: ["GET", "POST", "PUT", "DELETE", "OPTIONS"],
    headers: ["Authorization", "Content-Type", "Accept"]

  plug Plug.Parsers,
    parsers: [:urlencoded, :multipart, :json],
    pass: ["*/*"],
    json_decoder: Phoenix.json_library()

  plug Plug.MethodOverride
  plug Plug.Head
  plug SignalingServiceWeb.Router
end
