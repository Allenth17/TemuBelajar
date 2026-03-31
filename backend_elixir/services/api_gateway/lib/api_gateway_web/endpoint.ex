defmodule ApiGatewayWeb.Endpoint do
  use Phoenix.Endpoint, otp_app: :api_gateway

  socket("/socket", ApiGatewayWeb.UserSocket,
    websocket: [
      serializer: [
        {Phoenix.Socket.V2.JSONSerializer, "~> 2.0.0"},
        {Phoenix.Socket.V1.JSONSerializer, "~> 1.0.0"}
      ]
    ],
    longpoll: false
  )

  plug(CORSPlug,
    origin: "*",
    methods: ["GET", "POST", "PUT", "DELETE", "OPTIONS"],
    headers: ["Authorization", "Content-Type", "Accept"]
  )

  plug(Plug.RequestId)
  plug(Plug.Telemetry, event_prefix: [:phoenix, :endpoint])

  plug(Plug.Parsers,
    parsers: [:urlencoded, :multipart, :json],
    pass: ["*/*"],
    json_decoder: Phoenix.json_library()
  )

  plug(Plug.MethodOverride)
  plug(Plug.Head)
  plug(ApiGatewayWeb.Router)
end
