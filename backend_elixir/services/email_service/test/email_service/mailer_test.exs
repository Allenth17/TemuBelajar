defmodule EmailService.Mailer.EmailTest do
  @moduledoc """
  Tests for EmailService.Mailer.Email — the OTP email builder.

  Does NOT send real emails (no SMTP required).
  Validates structure, content, and encoding of Swoosh email structs only.
  """

  use ExUnit.Case, async: true

  alias EmailService.Mailer.Email

  # ── send_otp/2 ──────────────────────────────────────────────────────────────

  describe "send_otp/2" do
    test "returns a Swoosh.Email struct" do
      email = Email.send_otp("budi@ui.ac.id", "123456")
      assert %Swoosh.Email{} = email
    end

    test "sets the correct recipient" do
      email = Email.send_otp("budi@ui.ac.id", "123456")
      assert {"", "budi@ui.ac.id"} in email.to
    end

    test "sets the sender to TemuBelajar noreply address" do
      email = Email.send_otp("budi@ui.ac.id", "123456")
      assert {"TemuBelajar", "noreply@temubelajar.com"} = email.from
    end

    test "subject contains OTP keyword" do
      email = Email.send_otp("budi@ui.ac.id", "123456")
      assert String.contains?(email.subject, "OTP")
    end

    test "subject contains TemuBelajar branding" do
      email = Email.send_otp("budi@ui.ac.id", "123456")
      assert String.contains?(email.subject, "TemuBelajar")
    end

    test "text body contains the OTP code" do
      otp = "789012"
      email = Email.send_otp("budi@ui.ac.id", otp)
      assert String.contains?(email.text_body, otp)
    end

    test "HTML body contains the OTP code" do
      otp = "654321"
      email = Email.send_otp("alice@itb.ac.id", otp)
      assert String.contains?(email.html_body, otp)
    end

    test "HTML body is valid HTML (contains DOCTYPE)" do
      email = Email.send_otp("budi@ui.ac.id", "111111")
      assert String.contains?(email.html_body, "<!DOCTYPE html>")
    end

    test "HTML body mentions 2-minute expiry" do
      email = Email.send_otp("budi@ui.ac.id", "222222")
      assert String.contains?(email.html_body, "2 menit") or
             String.contains?(email.text_body, "2 menit")
    end

    test "accepts various valid OTP formats" do
      Enum.each(["000000", "999999", "123456"], fn otp ->
        email = Email.send_otp("test@ui.ac.id", otp)
        assert String.contains?(email.text_body, otp)
      end)
    end

    test "email struct has no nil required fields" do
      email = Email.send_otp("budi@ui.ac.id", "123456")
      assert email.to != nil
      assert email.from != nil
      assert email.subject != nil and email.subject != ""
      assert email.html_body != nil and email.html_body != ""
      assert email.text_body != nil and email.text_body != ""
    end

    test "different recipients produce separate email structs" do
      email_a = Email.send_otp("alice@ui.ac.id", "111111")
      email_b = Email.send_otp("bob@itb.ac.id", "222222")

      [{_, recipient_a}] = email_a.to
      [{_, recipient_b}] = email_b.to

      assert recipient_a == "alice@ui.ac.id"
      assert recipient_b == "bob@itb.ac.id"
    end

    test "different OTP codes produce different text bodies" do
      email_a = Email.send_otp("test@ui.ac.id", "111111")
      email_b = Email.send_otp("test@ui.ac.id", "999999")

      assert String.contains?(email_a.text_body, "111111")
      assert String.contains?(email_b.text_body, "999999")
      assert email_a.text_body != email_b.text_body
    end
  end
end
