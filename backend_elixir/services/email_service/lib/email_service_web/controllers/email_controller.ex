defmodule EmailServiceWeb.EmailController do
  use EmailServiceWeb, :controller

  alias EmailService.Mailer.Email

  # POST /api/send-otp
  def send_otp(conn, %{"email" => email, "otp" => otp}) do
    # Send OTP email asynchronously
    Task.start(fn ->
      try do
        Email.send_otp(email, otp) |> EmailService.Mailer.deliver()
      rescue
        _ -> :ok
      end
    end)

    json(conn, %{message: "OTP dikirim ke email", success: true})
  end
end
