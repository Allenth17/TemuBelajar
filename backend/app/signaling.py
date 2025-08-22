
from fastapi import APIRouter, WebSocket, WebSocketDisconnect
from typing import Dict
import json
from pathlib import Path

router = APIRouter()

# Simpan koneksi aktif
active_connections: Dict[str, WebSocket] = {}

# Path ke sessions.json
SESSIONS_FILE = Path("backend/data/sessions.json")

def load_sessions():
    if SESSIONS_FILE.exists():
        return json.loads(SESSIONS_FILE.read_text())
    return {}

@router.websocket("/ws/signaling")
async def websocket_endpoint(websocket: WebSocket):
    # Ambil token dari query param
    token = websocket.query_params.get("token")

    # Validasi token
    sessions = load_sessions()
    user_id = None
    for uid, sess in sessions.items():
        if sess.get("token") == token:
            user_id = uid
            break

    if not user_id:
        await websocket.close(code=1008)  # Policy Violation
        return

    # Terima koneksi
    await websocket.accept()
    active_connections[user_id] = websocket

    try:
        while True:
            data = await websocket.receive_json()
            target_id = data.get("target_id")
            if target_id in active_connections:
                await active_connections[target_id].send_json({
                    "from": user_id,
                    "type": data.get("type"),
                    "sdp": data.get("sdp"),
                    "candidate": data.get("candidate")
                })
    except WebSocketDisconnect:
        print(f"User {user_id} disconnected.")
        if user_id in active_connections:
            del active_connections[user_id]


