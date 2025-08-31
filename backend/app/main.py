import json
import os
import uuid
from datetime import datetime, timedelta, timezone

import bcrypt
from fastapi import FastAPI, HTTPException, Header, Depends
from fastapi import WebSocket, WebSocketDisconnect
from fastapi.exceptions import RequestValidationError
from fastapi.openapi.utils import get_openapi
from fastapi.requests import Request

from backend.app import signaling
from backend.app.email_utils import send_otp, is_valid_campus_email
from backend.app.matchmaking import add_to_queue, remove_from_queue
from backend.app.middleware.error_handler import error_handler
from backend.app.models import MatchRequest
from backend.app.models import (
    RegisterRequest,
    RegisterResponse,
    OtpVerificationRequest,
    LoginRequest,
    EmailRequest
)
from backend.app.stream_manager import add_stream, get_stream
from backend.app.stream_manager import remove_stream


app = FastAPI(
    title = "TemuBelajar API",
    description = "API for TemuBelajar video chat application",
    version = "1.0.0"
)

def custom_openapi():
    if app.openapi_schema:
        return app.openapi_schema
        
    openapi_schema = get_openapi(
        title = "TemuBelajar API",
        version = "1.0.0",
        description = "API for TemuBelajar video chat application",
        routes = app.routes,
    )
    
    app.openapi_schema = openapi_schema
    return app.openapi_schema

app.openapi = custom_openapi


# Register error_handler as an exception handler
@app.exception_handler(Exception)
async def generic_exception_handler(request: Request, exc: Exception):
    return await error_handler(request, exc)

@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError):
    return await error_handler(request, exc)

DB_FILE = "users.json"
SESSION_FILE = "sessions.json"

# Helpers to load/save with wrapped LIST format while keeping backward compatibility
# users.json -> { "users": [ { "email": ..., ... }, ... ] }
# sessions.json -> { "sessions": [ { "token": ..., "email": ..., "expired_at": ... }, ... ] }


def _read_json(file_path: str):
    if not os.path.exists(file_path):
        return None
    try:
        with open(file_path, "r") as f:
            return json.load(f)
    except (json.JSONDecodeError, FileNotFoundError):
        return None


def _write_json(file_path: str, data):
    with open(file_path, "w") as f:
        json.dump(data, f, indent=2)


# ----- Users (list) -----

def load_users_list() -> list:
    data = _read_json(DB_FILE)
    if not data:
        return []
    # already wrapped list
    if isinstance(data, dict) and isinstance(data.get("users"), list):
        return data["users"]
    # wrapped dict -> convert to list with email
    if isinstance(data, dict) and isinstance(data.get("users"), dict):
        return [{"email": email, **udata} for email, udata in data["users"].items()]
    # flat dict -> convert to list
    if isinstance(data, dict):
        return [{"email": email, **udata} for email, udata in data.items()]
    # flat list (unexpected but supported)
    if isinstance(data, list):
        return data
    return []


def save_users_list(users_list: list):
    _write_json(DB_FILE, { "users": users_list })


# ----- Sessions (list) -----

def load_sessions_list() -> list:
    data = _read_json(SESSION_FILE)
    if not data:
        return []
    # already wrapped list
    if isinstance(data, dict) and isinstance(data.get("sessions"), list):
        return data["sessions"]
    # wrapped dict -> convert to list with token
    if isinstance(data, dict) and isinstance(data.get("sessions"), dict):
        return [{"token": token, **sdata} for token, sdata in data["sessions"].items()]
    # flat dict -> convert to list
    if isinstance(data, dict):
        return [{"token": token, **sdata} for token, sdata in data.items()]
    if isinstance(data, list):
        return data
    return []


def save_sessions_list(sessions_list: list):
    _write_json(SESSION_FILE, {"sessions": sessions_list})


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

    sessions = load_sessions_list()
    if not sessions:
        raise HTTPException(status_code=401, detail="Sessions tidak ditemukan")

    session = next((s for s in sessions if s.get("token") == token), None)
    if not session:
        raise HTTPException(status_code=401, detail="Token tidak valid")

    if datetime.fromisoformat(session["expired_at"]) < datetime.now(timezone.utc):
        raise HTTPException(status_code=401, detail="Token kadaluarsa")

    return session["email"]

# Matchmaking endpoint
from backend.app.stream_manager import load_streams

def find_match(user_id: str):
    # Find any other user that currently has a stream
    try:
        streams = load_streams()
        for uid, info in streams.items():
            if uid != user_id:
                return uid
    except Exception:
        pass
    return None

@app.get("/")
def hello():
    return { "message": "backend is running" }


@app.post("/register", response_model=RegisterResponse)
def register(data: RegisterRequest):
    if not is_valid_campus_email(data.email):
        raise HTTPException(status_code = 400, detail = "Email bukan dari kampus yang diizinkan")

    otp = str(uuid.uuid4())[:6]
    now = datetime.now(timezone.utc).isoformat()

    if not os.path.exists(DB_FILE):
        save_users_list([])

    users = load_users_list()

    if any(u.get("email") == data.email for u in users):
        raise HTTPException(status_code = 409, detail = "Email sudah terdaftar")

    if any(u.get("username") == data.username for u in users):
        raise HTTPException(status_code = 409, detail = "Username sudah dipakai")

    users.append({
        "email": data.email,
        "otp": otp,
        "verified": False,
        "created_at": now,
        "password": hash_password(data.password),
        "name": data.name,
        "phone": data.phone,
        "university": data.university,
        "username": data.username
    })

    save_users_list(users)

    send_otp(data.email, otp)
    return { "success": True, "message": "Kode OTP dikirim ke email" }


@app.post("/verify-otp")
def verify_otp(data: OtpVerificationRequest):
    if not os.path.exists(DB_FILE):
        raise HTTPException(status_code = 404, detail = "Database tidak ditemukan")

    users = load_users_list()

    idx = next((i for i, u in enumerate(users) if u.get("email") == data.email), None)
    if idx is None:
        raise HTTPException(status_code = 404, detail = "Email tidak ditemukan")

    user_data = users[idx]
    created_at = datetime.fromisoformat(user_data.get("created_at"))
    if not user_data["verified"] and datetime.now(timezone.utc) - created_at > timedelta(minutes=5):
        users.pop(idx)
        save_users_list(users)
        raise HTTPException(status_code = 410, detail = "OTP kadaluarsa, silakan daftar ulang")

    if user_data["otp"] != data.otp:
        raise HTTPException(status_code = 400, detail = "OTP salah")

    user_data["verified"] = True
    users[idx] = user_data

    save_users_list(users)

    return { "message": "OTP berhasil diverifikasi" }


@app.post("/resend-otp")
def resend_otp(request: EmailRequest):
    # Pastikan database ada
    if not os.path.exists(DB_FILE):
        raise HTTPException(status_code=404, detail="Database tidak ditemukan")

    users = load_users_list()

    idx = next((i for i, u in enumerate(users) if u.get("email") == request.email), None)
    if idx is None:
        raise HTTPException(status_code = 404, detail = "Email tidak ditemukan")

    user = users[idx]
    if user.get("verified"):
        raise HTTPException(status_code = 400, detail = "Email sudah diverifikasi")

    # Generate dan simpan OTP baru
    otp = str(uuid.uuid4())[:6]
    user["otp"] = otp
    user["created_at"] = datetime.now(timezone.utc).isoformat()
    users[idx] = user

    # Tulis kembali ke file
    save_users_list(users)

    send_otp(request.email, otp)
    return { "message": "OTP baru telah dikirim" }


@app.post("/login")
def login(data: LoginRequest):
    if not os.path.exists(DB_FILE):
        raise HTTPException(status_code = 404, detail = "Database tidak ditemukan")

    users = load_users_list()

    # Cari user berdasarkan email atau username
    user = None
    user_email = None
    for u in users:
        if data.email_or_username == u.get("email") or data.email_or_username == u.get("username"):
            user = u
            user_email = u.get("email")
            break

    if not user:
        raise HTTPException(status_code = 404, detail = "Email/Username tidak ditemukan")

    if not user["verified"]:
        raise HTTPException(status_code = 403, detail = "Email belum diverifikasi")

    if not check_password(data.password, user["password"]):
        raise HTTPException(status_code = 401, detail = "Password salah")

    # Update last login
    user["last_login"] = datetime.now(timezone.utc).isoformat()
    uidx = next((i for i, u in enumerate(users) if u.get("email") == user_email), None)
    if uidx is not None:
        users[uidx] = user
    else:
        users.append(user)

    save_users_list(users)

    # Buat session token
    token = str(uuid.uuid4())
    expired_at = (datetime.now(timezone.utc) + timedelta(days=15)).isoformat()

    if not os.path.exists(SESSION_FILE):
        save_sessions_list([])

    sessions = load_sessions_list()

    sessions.append({
        "token": token,
        "email": user_email,
        "expired_at": expired_at
    })

    save_sessions_list(sessions)

    return {"token": token, "expires_in": "15 hari"}



@app.get("/me")
def get_profile(email: str = Depends(auth_required)):
    users = load_users_list()

    user = next((u for u in users if u.get("email") == email), None)
    if not user:
        raise HTTPException(status_code = 404, detail = "User tidak ditemukan")

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
        raise HTTPException(status_code = 400, detail = "Missing or invalid auth token")

    token = token.replace("Bearer ", "")
    if not os.path.exists(SESSION_FILE):
        raise HTTPException(status_code = 401, detail = "Invalid session token")

    sessions = load_sessions_list()
    idx = next((i for i, s in enumerate(sessions) if s.get("token") == token), None)
    if idx is not None:
        sessions.pop(idx)
        save_sessions_list(sessions)
        return {"message": "Logged out successfully"}
    else:
        raise HTTPException(status_code = 401, detail = "Invalid session token")


@app.delete("/expired-sessions")
def cleanup_expired_sessions():
    now = datetime.now(timezone.utc)
    if not os.path.exists(SESSION_FILE):
        save_sessions_list([])
        return {"message": "No sessions to clean"}

    sessions = load_sessions_list()
    new_sessions = [s for s in sessions if datetime.fromisoformat(s["expired_at"]) > now]
    save_sessions_list(new_sessions)
    return {"message": "Expired sessions dibersihkan"}

# WebSocket endpoint for matchmaking
app.include_router(signaling.router)
@app.websocket("/ws")
async def websocket_endpoint(ws: WebSocket):
    await ws.accept()
    await add_to_queue(ws)

    try:
        while True:
            data = await ws.receive_text()
    except WebSocketDisconnect:
        await remove_from_queue(ws)

# Endpoint to add user to matchmaking queue
@app.get("/get_stream/{user_id}")
def fetch_stream(user_id: str):
    stream = get_stream(user_id)
    if not stream:
        raise HTTPException(status_code = 404, detail = "No stream found.")
    return stream

# Endpoint to match user and return stream URL
@app.post("/match")
def match_user(request: MatchRequest):
    user_id = request.user_id
    stream_url = request.stream_url

    # Simpan stream URL user lebih dulu
    add_stream(user_id, stream_url)

    # Coba cari pasangan yang sudah aktif
    target_user_id = find_match(user_id)

    if not target_user_id:
        return {
            "matched_with": None,
            "message": "Waiting for another user to join..."
        }

    # Cek apakah target udah punya stream
    target_stream = get_stream(target_user_id)
    if target_stream:
        return {
            "matched_with": target_user_id,
            "target_stream": target_stream
        }

    return {
        "matched_with": target_user_id,
        "message": "Waiting for target stream..."
    }

# Endpoint to remove a user from streaming
@app.post("/disconnect/{user_id}")
def disconnect_user(user_id: str):
    removed = remove_stream(user_id)
    if not removed:
        raise HTTPException(status_code = 404, detail = "User not streaming.")
    return { "message": f"User {user_id} disconnected." }

# Endpoint to list all active streams
@app.get("/streams")
def list_streams():
    streams = load_streams()  # dari stream_manager
    return streams

# The signaling router now handles the signaling WebSocket endpoint