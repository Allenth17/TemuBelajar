import os
from dotenv import load_dotenv
from email.message import EmailMessage
import smtplib

load_dotenv()

ALLOWED_DOMAINS = [
    "student.uns.ac.id", "student.ui.ac.id", "student.ugm.ac.id"
]

def is_valid_campus_email(email: str) -> bool:
    return any(email.endswith("@" + domain) for domain in ALLOWED_DOMAINS)

def send_otp(to_email: str, otp: str):
    EMAIL = os.getenv("SMTP_EMAIL")
    PASSWORD = os.getenv("SMTP_PASS")

    msg = EmailMessage()
    msg.set_content(f"Kode OTP kamu: {otp}")
    msg["Subject"] = "Verifikasi Email Kampus"
    msg["From"] = EMAIL
    msg["To"] = to_email

    with smtplib.SMTP_SSL("smtp.gmail.com", 465) as smtp:
        smtp.login(EMAIL, PASSWORD)
        smtp.send_message(msg)