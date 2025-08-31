import uuid
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
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from backend.app.database import get_db, AsyncSessionLocal
from backend.app.db_models import User as DBUser, Session as DBSession
from sqlalchemy.ext.asyncio import AsyncSession
from backend.app.database import engine
from backend.app.db_models import Base as DBBase
from backend.app.stream_manager import load_streams
from datetime import datetime, timezone, timedelta

app = FastAPI(
    title = "TemuBelajar API",
    description = "API for TemuBelajar video chat application",
    version = "1.0.0"
)

OTP_EXPIRY_MINUTES = 2

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

@app.on_event("startup")
async def on_startup():
    async with engine.begin() as conn:
        await conn.run_sync(DBBase.metadata.create_all)

@app.exception_handler(Exception)
async def generic_exception_handler(request: Request, exc: Exception):
    return await error_handler(request, exc)

@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError):
    return await error_handler(request, exc)

def hash_password(password: str) -> str:
    return bcrypt.hashpw(password.encode('utf-8'), bcrypt.gensalt()).decode('utf-8')

def check_password(password: str, hashed: str) -> bool:
    return bcrypt.checkpw(password.encode('utf-8'), hashed.encode('utf-8'))

async def auth_required(authorization: str = Header(None), db: AsyncSession = Depends(get_db)):
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Token tidak ditemukan")

    token = authorization.replace("Bearer ", "")

    q = await db.execute(select(DBSession).where(DBSession.token == token))
    sess = q.scalars().first()
    if not sess:
        raise HTTPException(status_code=401, detail="Token tidak valid")
    # expired_at is naive DateTime in DB (UTC). Compare against current UTC (naive UTC)
    now_naive_utc = datetime.utcnow()
    if sess.expired_at and sess.expired_at < now_naive_utc:
        raise HTTPException(status_code=401, detail="Token kadaluarsa")
    return sess.email

def find_match(user_id: str):
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
async def register(data: RegisterRequest, db: AsyncSession = Depends(get_db)):
    if not is_valid_campus_email(data.email):
        raise HTTPException(status_code=400, detail="Email bukan dari kampus yang diizinkan")

    otp = str(uuid.uuid4())[:6]
    now_utc = datetime.now(timezone.utc)

    # cek duplikasi
    q = await db.execute(select(DBUser).where(DBUser.email == data.email))
    if q.scalars().first():
        raise HTTPException(status_code=409, detail="Email sudah terdaftar")
    q2 = await db.execute(select(DBUser).where(DBUser.username == data.username))
    if q2.scalars().first():
        raise HTTPException(status_code=409, detail="Username sudah dipakai")

    user = DBUser(
        email=data.email,
        otp=otp,
        verified=False,
        created_at=now_utc,
        otp_created_at=now_utc,
        password=hash_password(data.password),
        name=data.name,
        phone=data.phone,
        university=data.university,
        username=data.username,
        last_login=None
    )
    db.add(user)
    try:
        await db.commit()
    except IntegrityError:
        await db.rollback()
        raise HTTPException(status_code=409, detail="User sudah ada")

    send_otp(data.email, otp)
    return { "success": True, "message": "Kode OTP dikirim ke email" }

@app.post("/verify-otp")
async def verify_otp(data: OtpVerificationRequest, db: AsyncSession = Depends(get_db)):
    q = await db.execute(select(DBUser).where(DBUser.email == data.email))
    user = q.scalars().first()
    if not user:
        raise HTTPException(status_code=404, detail="Email tidak ditemukan")

    otp_ts = user.otp_created_at or user.created_at
    if otp_ts is None:
        raise HTTPException(status_code=400, detail="Data registrasi tidak lengkap")

    if otp_ts.tzinfo is None:
        otp_ts = otp_ts.replace(tzinfo=timezone.utc)
    else:
        otp_ts = otp_ts.astimezone(timezone.utc)

    now_utc = datetime.now(timezone.utc)

    if not user.verified and (now_utc - otp_ts) > timedelta(minutes=OTP_EXPIRY_MINUTES):
        await db.delete(user)
        await db.commit()
        raise HTTPException(status_code=410, detail="OTP kadaluarsa, silakan daftar ulang")

    if not user.otp or user.otp != data.otp:
        raise HTTPException(status_code=400, detail="OTP salah")

    user.verified = True
    user.otp = None
    user.otp_created_at = None
    user.last_login = now_utc
    await db.commit()

    return { "message": "OTP berhasil diverifikasi" }

@app.post("/resend-otp")
async def resend_otp(request: EmailRequest, db: AsyncSession = Depends(get_db)):
    q = await db.execute(select(DBUser).where(DBUser.email == request.email))
    user = q.scalars().first()
    if not user:
        raise HTTPException(status_code = 404, detail = "Email tidak ditemukan")
    if user.verified:
        raise HTTPException(status_code = 400, detail = "Email sudah diverifikasi")
    otp = str(uuid.uuid4())[:6]
    now_utc = datetime.now(timezone.utc)
    user.otp = otp
    user.otp_created_at = now_utc
    await db.commit()
    send_otp(request.email, otp)
    return {"message": "OTP baru telah dikirim"}



@app.post("/login")
async def login(data: LoginRequest, db: AsyncSession = Depends(get_db)):
    q = await db.execute(
        select(DBUser).where((DBUser.email == data.email_or_username) | (DBUser.username == data.email_or_username))
    )
    user = q.scalars().first()
    if not user:
        raise HTTPException(status_code=404, detail="Email/Username tidak ditemukan")
    if not user.verified:
        raise HTTPException(status_code=403, detail="Email belum diverifikasi")
    if not check_password(data.password, user.password):
        raise HTTPException(status_code=401, detail="Password salah")

    user.last_login = datetime.utcnow()
    await db.commit()

    token = str(uuid.uuid4())
    expired_at = datetime.utcnow() + timedelta(days=15)
    sess = DBSession(token=token, email=user.email, expired_at=expired_at)
    db.add(sess)
    await db.commit()

    return {"token": token, "expires_in": "15 hari"}

@app.get("/me")
async def get_profile(email: str = Depends(auth_required), db: AsyncSession = Depends(get_db)):
    q = await db.execute(select(DBUser).where(DBUser.email == email))
    user = q.scalars().first()
    if not user:
        raise HTTPException(status_code=404, detail="User tidak ditemukan")

    last_login = None
    if user.last_login:
        if user.last_login.tzinfo is None:
            last_login = user.last_login.replace(tzinfo=timezone.utc).isoformat()
        else:
            last_login = user.last_login.isoformat()
    return {
        "email": email,
        "name": user.name,
        "phone": user.phone,
        "university": user.university,
        "last_login": last_login,
        "message": "Token valid"
    }

@app.post("/logout")
async def logout(request: Request, db: AsyncSession = Depends(get_db)):
    token = request.headers.get("Authorization")
    if not token or not token.startswith("Bearer "):
        raise HTTPException(status_code=400, detail="Missing or invalid auth token")
    token = token.replace("Bearer ", "")
    q = await db.execute(select(DBSession).where(DBSession.token == token))
    sess = q.scalars().first()
    if not sess:
        raise HTTPException(status_code=401, detail="Invalid session token")
    await db.delete(sess)
    await db.commit()
    return { "message": "Logged out successfully" }

@app.delete("/expired-sessions")
async def cleanup_expired_sessions(db: AsyncSession = Depends(get_db)):
    now_naive_utc = datetime.utcnow()
    q = await db.execute(select(DBSession).where(DBSession.expired_at < now_naive_utc))
    expired = q.scalars().all()
    for s in expired:
        await db.delete(s)
    await db.commit()
    return { "message": "Expired sessions dibersihkan" }

app.include_router(signaling.router)
@app.websocket("/ws")
async def websocket_endpoint(ws: WebSocket):
    # Optional token via query param for basic auth on matchmaking
    token = ws.query_params.get("token")
    # fallback to Authorization header
    if not token:
        auth = ws.headers.get("Authorization")
        if auth and auth.startswith("Bearer "):
            token = auth.replace("Bearer ", "")
    user_email = None
    if not token:
        await ws.close(code=1008)
        return
    # Validate token using the same DB sessions table
    async with AsyncSessionLocal() as db:  # type: ignore
        q = await db.execute(select(DBSession).where(DBSession.token == token))
        sess = q.scalars().first()
        if sess and (not sess.expired_at or sess.expired_at > datetime.utcnow()):
            user_email = sess.email
        else:
            await ws.close(code=1008)
            return

    await ws.accept()
    await add_to_queue(ws)

    # Inform client that it is queued (+ echo email if known)
    try:
        payload = {"type": "queued"}
        if user_email:
            payload["user_id"] = user_email
        await ws.send_json(payload)
    except Exception:
        await remove_from_queue(ws)
        await ws.close()
        return

    # Lightweight heartbeat: server pings every ~25s expecting client pong
    from asyncio import create_task, Event, sleep
    stop = Event()

    async def heartbeat():
        misses = 0
        while not stop.is_set():
            try:
                await ws.send_json({"type": "ping"})
                # allow client to respond within 15s elsewhere in loop
                await sleep(25)
                # misses handled in main loop upon timeouts
            except Exception:
                break

    hb_task = create_task(heartbeat())

    try:
        while True:
            msg = await ws.receive_text()
            if len(msg) > 16 * 1024:
                await ws.send_json({"type": "error", "message": "Payload too large"})
                continue
            if msg == "ping":
                await ws.send_text("pong")
            elif msg == "leave":
                await ws.send_json({"type": "bye"})
                break
            # other messages are ignored by the simple queue
    except WebSocketDisconnect:
        pass
    finally:
        stop.set()
        try:
            hb_task.cancel()
        except Exception:
            pass
        await remove_from_queue(ws)
        try:
            await ws.close()
        except Exception:
            pass

@app.get("/get_stream/{user_id}")
def fetch_stream(user_id: str):
    stream = get_stream(user_id)
    if not stream:
        raise HTTPException(status_code = 404, detail = "No stream found.")
    return stream

@app.post("/match")
def match_user(request: MatchRequest):
    user_id = request.user_id
    stream_url = request.stream_url
    add_stream(user_id, stream_url)
    target_user_id = find_match(user_id)

    if not target_user_id:
        return {
            "matched_with": None,
            "message": "Waiting for another user to join..."
        }

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

@app.post("/disconnect/{user_id}")
def disconnect_user(user_id: str):
    removed = remove_stream(user_id)
    if not removed:
        raise HTTPException(status_code = 404, detail = "User not streaming.")
    return { "message": f"User {user_id} disconnected." }

@app.get("/streams")
def list_streams():
    streams = load_streams()
    return streams