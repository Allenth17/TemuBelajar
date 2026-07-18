defmodule EmailServiceWeb.Router do
  use EmailServiceWeb, :router

  pipeline :api do
    plug(:accepts, ["json"])
  end

  # All routes below require the gateway / auth_service HMAC secret. Phase 0.5 —
  # /api/send-otp was an open email relay before this gate.
  pipeline :internal do
    plug(EmailServiceWeb.Plugs.InternalAuth)
  end

  scope "/api", EmailServiceWeb do
    pipe_through(:api)
    get("/health", HealthController, :health)
  end

  scope "/api", EmailServiceWeb do
    pipe_through([:api, :internal])
    post("/send-otp", EmailController, :send_otp)
  end
end
