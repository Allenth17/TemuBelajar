defmodule TemuBelajar.Mailer.Email do
  import Swoosh.Email

  def send_otp(to_email, otp) do
    new()
    |> to(to_email)
    |> from({"TemuBelajar", "noreply@temubelajar.com"})
    |> subject("[TemuBelajar] Kode OTP Verifikasi")
    |> html_body(otp_html(otp))
    |> text_body("Kode OTP kamu: #{otp}\nBerlaku selama 2 menit.")
  end

  defp otp_html(otp) do
    """
    <!DOCTYPE html>
    <html>
    <body style="font-family: Arial, sans-serif; background: #0f0f1a; color: #e0e0ff; padding: 40px;">
      <div style="max-width: 480px; margin: auto; background: #1a1a2e; border-radius: 16px; padding: 40px; border: 1px solid #6c63ff33;">
        <h2 style="color: #6c63ff; margin-top: 0;">TemuBelajar</h2>
        <p>Halo! Berikut kode OTP untuk verifikasi akunmu:</p>
        <div style="text-align: center; margin: 32px 0;">
          <span style="font-size: 48px; font-weight: bold; letter-spacing: 12px; color: #6c63ff;">#{otp}</span>
        </div>
        <p style="color: #999; font-size: 14px;">Kode ini berlaku selama <strong>2 menit</strong>. Jangan bagikan ke siapapun.</p>
        <hr style="border-color: #6c63ff33; margin: 24px 0;">
        <p style="color: #666; font-size: 12px;">Jika kamu tidak mendaftar di TemuBelajar, abaikan email ini.</p>
      </div>
    </body>
    </html>
    """
  end
end
