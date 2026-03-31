defmodule TemuBelajar.Mailer.EmailTest do
  use ExUnit.Case, async: true
  alias TemuBelajar.Mailer.Email

  test "send_otp/2 creates a valid email" do
    email = Email.send_otp("test@kampus.ac.id", "123456")

    assert email.to == [{"", "test@kampus.ac.id"}]
    assert email.from == {"TemuBelajar", "noreply@temubelajar.com"}
    assert email.subject == "[TemuBelajar] Kode OTP Verifikasi"
    assert email.text_body =~ "Kode OTP kamu: 123456"
    assert email.html_body =~ "123456"
  end
end
