
from fastapi import APIRouter, WebSocket, WebSocketDisconnect
from typing import Dict
import json
from pathlib import Path

router = APIRouter()


# Simpan koneksi aktif
active_connections: Dict[str, WebSocket] = {}

# Path ke sessions.json (gunakan file utama yang dipakai oleh main.py)
SESSIONS_FILE = Path("sessions.json")


def load_sessions_list():
    if not SESSIONS_FILE.exists():
        return []
    try:
        data = json.loads(SESSIONS_FILE.read_text())
        if isinstance(data, dict) and isinstance(data.get("sessions"), list):
            return data["sessions"]
        if isinstance(data, list):
            return data
        if isinstance(data, dict):
            # fallback: convert dict of token->session to list
            return [{"token": k, **v} for k, v in data.items()]
    except Exception:
        return []
    return []

@router.websocket("/ws/signaling")
async def websocket_endpoint(websocket: WebSocket):
    # Ambil token dari query param
    token = websocket.query_params.get("token")

    # Validasi token pada sessions.json (format list)
    sessions = load_sessions_list()
    user_id = None  # gunakan email sebagai user_id
    for sess in sessions:
        if sess.get("token") == token:
            user_id = sess.get("email")
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


