
from fastapi import APIRouter, WebSocket, WebSocketDisconnect
from typing import Dict
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from backend.app.database import AsyncSessionLocal
from backend.app.db_models import Session as DBSession
import asyncio
import json
import time

router = APIRouter()

# Simpan koneksi aktif
active_connections: Dict[str, WebSocket] = {}

# Optional: simple per-connection rate limiter state (token bucket-ish)
class RateLimiter:
    def __init__(self, max_messages: int = 30, per_seconds: int = 10):
        self.max_messages = max_messages
        self.per_seconds = per_seconds
        self.reset_time = time.monotonic()
        self.count = 0

    def allow(self) -> bool:
        now = time.monotonic()
        if now - self.reset_time > self.per_seconds:
            self.reset_time = now
            self.count = 0
        self.count += 1
        return self.count <= self.max_messages

@router.websocket("/ws/signaling")
async def websocket_endpoint(websocket: WebSocket):
    # Ambil token dari query param (for compatibility). In future, prefer header/subprotocol.
    # Support token via query string or Authorization header
    token = websocket.query_params.get("token")
    if not token:
        token = websocket.headers.get("Authorization")
        if token and token.startswith("Bearer "):
            token = token.replace("Bearer ", "")

    # Validasi token pada DB
    async with AsyncSessionLocal() as db:  # create a short-lived session for auth
        q = await db.execute(select(DBSession).where(DBSession.token == token))
        sess = q.scalars().first()
        user_id = sess.email if sess else None

    if not user_id:
        await websocket.close(code=1008)  # Policy Violation
        return

    # Terima koneksi
    await websocket.accept()
    active_connections[user_id] = websocket

    # Send welcome
    try:
        await websocket.send_json({"type": "welcome", "user_id": user_id})
    except Exception:
        await websocket.close(code=1011)
        active_connections.pop(user_id, None)
        return

    # Heartbeat: server -> client ping every 20s
    stop = asyncio.Event()

    async def heartbeat():
        while not stop.is_set():
            try:
                await websocket.send_json({"type": "ping"})
                await asyncio.sleep(20)
            except Exception:
                break

    hb_task = asyncio.create_task(heartbeat())
    limiter = RateLimiter(max_messages=50, per_seconds=10)

    try:
        while True:
            # Receive as text to cap size
            msg = await websocket.receive_text()
            if len(msg) > 64 * 1024:
                await websocket.send_json({"type": "error", "message": "Payload too large"})
                continue
            try:
                data = json.loads(msg)
            except Exception:
                await websocket.send_json({"type": "error", "message": "Invalid JSON"})
                continue

            if not isinstance(data, dict):
                await websocket.send_json({"type": "error", "message": "Invalid message format"})
                continue

            if not limiter.allow():
                await websocket.send_json({"type": "error", "message": "Rate limit exceeded"})
                continue

            msg_type = data.get("type")
            target_id = data.get("target_id")
            pair_id = data.get("pair_id")  # optional, for future validation

            if msg_type == "pong":
                # client responded to heartbeat
                continue
            if msg_type == "ping":
                await websocket.send_json({"type": "pong"})
                continue
            if msg_type == "leave":
                if target_id and target_id in active_connections:
                    try:
                        await active_connections[target_id].send_json({"type": "peer_left", "from": user_id})
                    except Exception:
                        pass
                await websocket.send_json({"type": "bye"})
                await websocket.close(code=1000)
                break

            if not target_id:
                await websocket.send_json({"type": "error", "message": "target_id required"})
                continue

            target_ws = active_connections.get(target_id)
            if not target_ws:
                await websocket.send_json({"type": "delivery_status", "status": "offline", "target_id": target_id})
                continue

            # Optional: if pair_id provided by client, include it for client-side validation.
            relay = {"from": user_id, "type": msg_type}
            if "sdp" in data:
                relay["sdp"] = data.get("sdp")
            if "candidate" in data:
                relay["candidate"] = data.get("candidate")
            if pair_id:
                relay["pair_id"] = pair_id

            try:
                await target_ws.send_json(relay)
                await websocket.send_json({"type": "delivery_status", "status": "sent", "target_id": target_id})
            except Exception:
                await websocket.send_json({"type": "delivery_status", "status": "failed", "target_id": target_id})
    except WebSocketDisconnect:
        try:
            await websocket.close()
        except Exception:
            pass
    finally:
        stop.set()
        try:
            hb_task.cancel()
        except Exception:
            pass
        active_connections.pop(user_id, None)


