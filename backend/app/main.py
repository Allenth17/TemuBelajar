from fastapi import FastAPI, HTTPException
from backend.app.models import RegisterRequest, RegisterResponse
from backend.app.email_utils import is_valid_campus_email, send_otp
from backend.app.models import OtpVerificationRequest


import uuid
import json
import os

app = FastAPI()
DB_FILE = "users.json"  # Simulasi database

@app.get("/")
def hello():
    return {"message : backend is running"}

from datetime import datetime

@app.post("/register", response_model=RegisterResponse)
def register(data: RegisterRequest):
    if not is_valid_campus_email(data.email):
        raise HTTPException(status_code=400, detail="Email bukan dari kampus yang diizinkan")

    otp = str(uuid.uuid4())[:6]
    now = datetime.utcnow().isoformat()

    if not os.path.exists(DB_FILE):
        with open(DB_FILE, "w") as f:
            json.dump({}, f)

    with open(DB_FILE, "r+") as file:
        users = json.load(file)
        users[data.email] = {
            "otp": otp,
            "verified": False,
            "created_at": now  # Simpan waktu OTP dibuat
        }
        file.seek(0)
        json.dump(users, file, indent=2)

    send_otp(data.email, otp)
    return {"success": True, "message": "kode OTP dikirim ke email"}

from datetime import datetime, timedelta

@app.post("/verify-otp")
def verify_otp(data: OtpVerificationRequest):
    if not os.path.exists(DB_FILE):
        raise HTTPException(status_code=404, detail="Database tidak ditemukan")

    with open(DB_FILE, "r+") as f:
        users = json.load(f)

    user_data = users.get(data.email)
    if not user_data:
        raise HTTPException(status_code=404, detail="Email tidak ditemukan")

    # Cek waktu
    created_at = datetime.fromisoformat(user_data.get("created_at"))
    if not user_data["verified"] and datetime.utcnow() - created_at > timedelta(minutes=5):
        del users[data.email]
        with open(DB_FILE, "w") as f:
            json.dump(users, f, indent=2)
        raise HTTPException(status_code=410, detail="OTP kadaluarsa, silakan daftar ulang")

    if user_data["otp"] != data.otp:
        raise HTTPException(status_code=400, detail="OTP salah")

    # OTP cocok → tandai sebagai verified
    user_data["verified"] = True
    users[data.email] = user_data

    with open(DB_FILE, "w") as f:
        json.dump(users, f, indent=2)

    return {"message": "OTP berhasil diverifikasi"}