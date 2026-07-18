defmodule SocialServiceWeb.Endpoint do
  use Phoenix.Endpoint, otp_app: :social_service

  # Phase 8.16 — origin allowlist resolved at request-time from Application
  # env (:cors_origins) by SocialServiceWeb.CORS. Replaces `origin: "*"`.
  plug(CORSPlug,
    origin: &SocialServiceWeb.CORS.origins/0,
    methods: ["GET", "POST", "PUT", "DELETE", "OPTIONS"],
    headers: ["Authorization", "Content-Type", "Accept"]
  )

  plug(Plug.Parsers,
    parsers: [:urlencoded, :multipart, :json],
    pass: ["*/*"],
    json_decoder: Phoenix.json_library()
  )

  plug(Plug.MethodOverride)
  plug(Plug.Head)
  plug(SocialServiceWeb.Router)
end
