from fastapi import FastAPI, HTTPException

from backend.app.models import (
    RegisterRequest,
    RegisterResponse,
    OtpVerificationRequest,
    LoginRequest,
    EmailRequest
)
from backend.app.email_utils import send_otp, is_valid_campus_email

from fastapi import Header


import uuid
import json
import os

app = FastAPI()
DB_FILE = "users.json"  # Simulasi database

@app.get("/")
def hello():
    return {"message": "backend is running"}

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

@app.post("/resend-otp")
def resend_otp(request: EmailRequest):
    with open("users.json", "r+") as f:
        users = json.load(f)

    user = users.get(request.email)
    if not user:
        raise HTTPException(status_code=404, detail="Email tidak ditemukan")

    if user["verified"]:
        raise HTTPException(status_code=400, detail="Email sudah diverifikasi")

    otp = str(uuid.uuid4())[:6]
    user["otp"] = otp
    user["created_at"] = datetime.utcnow().isoformat()
    users[request.email] = user

    f.seek(0)
    json.dump(users, f, indent=2)
    f.truncate()

    send_otp(request.email, otp)
    return {"message": "OTP baru telah dikirim"}

@app.post("/login")
def login(data: LoginRequest):
    with open("users.json", "r+") as f:
        users = json.load(f)

    user = users.get(data.email)
    if not user:
        raise HTTPException(status_code=404, detail="Email tidak ditemukan")
    
    if not user["verified"]:
        raise HTTPException(status_code=403, detail="Email belum diverifikasi")

    if user["otp"] != data.otp:
        raise HTTPException(status_code=401, detail="OTP salah")

    # Update last login
    user["last_login"] = datetime.utcnow().isoformat()
    users[data.email] = user
    f.seek(0)
    json.dump(users, f, indent=2)
    f.truncate()

    # Buat session
    token = str(uuid.uuid4())
    expired_at = (datetime.utcnow() + timedelta(days=15)).isoformat()

    if not os.path.exists("sessions.json"):
        with open("sessions.json", "w") as f:
            json.dump({}, f)

    with open("sessions.json", "r+") as f:
        sessions = json.load(f)
        sessions[token] = {
            "email": data.email,
            "expired_at": expired_at
        }
        f.seek(0)
        json.dump(sessions, f, indent=2)
        f.truncate()

    return {"token": token, "expires_in": "15 hari"}

def get_user_by_token(token: str):
    if not os.path.exists("sessions.json"):
        return None

    with open("sessions.json", "r") as f:
        sessions = json.load(f)

    session = sessions.get(token)
    if not session:
        return None

    if datetime.fromisoformat(session["expired_at"]) < datetime.utcnow():
        return None

    return session["email"]

@app.get("/me")
def get_profile(authorization: str = Header(None)):
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Token tidak ditemukan")

    token = authorization.replace("Bearer ", "")
    email = get_user_by_token(token)
    if not email:
        raise HTTPException(status_code=401, detail="Token tidak valid atau sudah expired")

    return {"email": email, "message": "Token masih berlaku"}

@app.delete("/expired-sessions")
def cleanup_expired_sessions():
    now = datetime.utcnow()
    with open("sessions.json", "r+") as f:
        sessions = json.load(f)
        new_sessions = {
            token: data
            for token, data in sessions.items()
            if datetime.fromisoformat(data["expired_at"]) > now
        }
        f.seek(0)
        json.dump(new_sessions, f, indent=2)
        f.truncate()
    return {"message": "Expired sessions dibersihkan"}


