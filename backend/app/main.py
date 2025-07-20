from fastapi import FastAPI, HTTPException, Header, Request, Depends
from backend.app.models import (
    RegisterRequest,
    RegisterResponse,
    OtpVerificationRequest,
    LoginRequest,
    EmailRequest
)
from backend.app.email_utils import send_otp, is_valid_campus_email
from datetime import datetime, timedelta
import uuid
import json
import os
import bcrypt

app = FastAPI()

DB_FILE = "users.json"
SESSION_FILE = "sessions.json"


# Utility: hash password
def hash_password(password: str) -> str:
    return bcrypt.hashpw(password.encode('utf-8'), bcrypt.gensalt()).decode('utf-8')


# Utility: cek hash password
def check_password(password: str, hashed: str) -> bool:
    return bcrypt.checkpw(password.encode('utf-8'), hashed.encode('utf-8'))


# Middleware untuk auth
def auth_required(authorization: str = Header(None)):
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Token tidak ditemukan")

    token = authorization.replace("Bearer ", "")

    if not os.path.exists(SESSION_FILE):
        raise HTTPException(status_code=401, detail="Sessions tidak ditemukan")

    with open(SESSION_FILE, "r") as sf:
        sessions = json.load(sf)

    session = sessions.get(token)
    if not session:
        raise HTTPException(status_code=401, detail="Token tidak valid")

    if datetime.fromisoformat(session["expired_at"]) < datetime.utcnow():
        raise HTTPException(status_code=401, detail="Token kadaluarsa")

    return session["email"]


@app.get("/")
def hello():
    return {"message": "backend is running"}


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

        # Cek apakah email sudah terdaftar
        if data.email in users:
            raise HTTPException(status_code=409, detail="Email sudah terdaftar")

        # Cek apakah username sudah dipakai
        if any(u.get("username") == data.username for u in users.values()):
            raise HTTPException(status_code=409, detail="Username sudah dipakai")

        users[data.email] = {
            "otp": otp,
            "verified": False,
            "created_at": now,
            "password": hash_password(data.password),
            "name": data.name,
            "phone": data.phone,
            "university": data.university,
            "username": data.username
        }

        file.seek(0)
        json.dump(users, file, indent=2)
        file.truncate()

    send_otp(data.email, otp)
    return {"success": True, "message": "Kode OTP dikirim ke email"}


@app.post("/verify-otp")
def verify_otp(data: OtpVerificationRequest):
    if not os.path.exists(DB_FILE):
        raise HTTPException(status_code=404, detail="Database tidak ditemukan")

    with open(DB_FILE, "r+") as f:
        users = json.load(f)

    user_data = users.get(data.email)
    if not user_data:
        raise HTTPException(status_code=404, detail="Email tidak ditemukan")

    created_at = datetime.fromisoformat(user_data.get("created_at"))
    if not user_data["verified"] and datetime.utcnow() - created_at > timedelta(minutes=5):
        del users[data.email]
        with open(DB_FILE, "w") as f:
            json.dump(users, f, indent=2)
        raise HTTPException(status_code=410, detail="OTP kadaluarsa, silakan daftar ulang")

    if user_data["otp"] != data.otp:
        raise HTTPException(status_code=400, detail="OTP salah")

    user_data["verified"] = True
    users[data.email] = user_data

    with open(DB_FILE, "w") as f:
        json.dump(users, f, indent=2)

    return {"message": "OTP berhasil diverifikasi"}


@app.post("/resend-otp")
def resend_otp(request: EmailRequest):
    with open(DB_FILE, "r+") as f:
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
    if not os.path.exists("users.json"):
        raise HTTPException(status_code=404, detail="Database tidak ditemukan")

    with open("users.json", "r") as f:
        users = json.load(f)

    # Cari user berdasarkan email atau username
    user = None
    for email, u in users.items():
        if data.email_or_username == email or data.email_or_username == u.get("username"):
            user = u
            user_email = email
            break

    if not user:
        raise HTTPException(status_code=404, detail="Email/Username tidak ditemukan")

    if not user["verified"]:
        raise HTTPException(status_code=403, detail="Email belum diverifikasi")

    if not check_password(data.password, user["password"]):
        raise HTTPException(status_code=401, detail="Password salah")

    # Update last login
    user["last_login"] = datetime.utcnow().isoformat()
    users[user_email] = user

    with open("users.json", "w") as f:
        json.dump(users, f, indent=2)

    # Buat session token
    token = str(uuid.uuid4())
    expired_at = (datetime.utcnow() + timedelta(days=15)).isoformat()

    if not os.path.exists("sessions.json"):
        with open("sessions.json", "w") as sf:
            json.dump({}, sf)

    with open("sessions.json", "r") as sf:
        sessions = json.load(sf)

    sessions[token] = {
        "email": user_email,
        "expired_at": expired_at
    }

    with open("sessions.json", "w") as sf:
        json.dump(sessions, sf, indent=2)

    return {"token": token, "expires_in": "15 hari"}



@app.get("/me")
def get_profile(email: str = Depends(auth_required)):
    with open(DB_FILE, "r") as f:
        users = json.load(f)

    user = users.get(email)
    if not user:
        raise HTTPException(status_code=404, detail="User tidak ditemukan")

    return {
        "email": email,
        "name": user.get("name"),
        "phone": user.get("phone"),
        "university": user.get("university"),
        "last_login": user.get("last_login"),
        "message": "Token valid"
    }


@app.post("/logout")
def logout(request: Request):
    token = request.headers.get("Authorization")
    if not token or not token.startswith("Bearer "):
        raise HTTPException(status_code=400, detail="Missing or invalid auth token")

    token = token.replace("Bearer ", "")
    with open(SESSION_FILE, "r+") as f:
        sessions = json.load(f)
        if token in sessions:
            del sessions[token]
            f.seek(0)
            f.truncate()
            json.dump(sessions, f)
            return {"message": "Logged out successfully"}
        else:
            raise HTTPException(status_code=401, detail="Invalid session token")


@app.delete("/expired-sessions")
def cleanup_expired_sessions():
    now = datetime.utcnow()
    with open(SESSION_FILE, "r+") as f:
        sessions = json.load(f)
        new_sessions = {
            token: data for token, data in sessions.items()
            if datetime.fromisoformat(data["expired_at"]) > now
        }
        f.seek(0)
        f.truncate()
        json.dump(new_sessions, f, indent=2)
    return {"message": "Expired sessions dibersihkan"}
