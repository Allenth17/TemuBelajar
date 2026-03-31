defmodule MatchmakingServiceWeb.Endpoint do
  use Phoenix.Endpoint, otp_app: :matchmaking_service

  socket "/socket", MatchmakingServiceWeb.UserSocket,
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
  plug MatchmakingServiceWeb.Router
end
