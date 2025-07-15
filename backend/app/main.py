from fastapi import FastAPI, HTTPException
from backend.app.models import RegisterRequest, RegisterResponse
from backend.app.email_utils import is_valid_campus_email, send_otp


import uuid
import json
import os

app = FastAPI()
DB_FILE = "users.json"  # Simulasi database

@app.get("/")
def hello():
    return {"message : backend is running"}

@app.post("/register", response_model=RegisterResponse)
def register(data: RegisterRequest):
    if not is_valid_campus_email(data.email):
        raise HTTPException(status_code=400, detail="Email bukan dari kampus yang diizinkan")

    otp = str(uuid.uuid4())[:6]

    if not os.path.exists(DB_FILE):
        with open(DB_FILE, "w") as f:
            json.dump({}, f)

    with open(DB_FILE, "r+") as file:
        users = json.load(file)
        users[data.email] = {"otp": otp, "verified": False}
        file.seek(0)
        json.dump(users, file, indent=2)

    send_otp(data.email, otp)
    return {"success": True, "message": "kode OTP dikirim ke email"}