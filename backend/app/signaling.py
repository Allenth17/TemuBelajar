import logging
from fastapi import APIRouter, WebSocket, WebSocketDisconnect
from typing import Dict
import json
from pathlib import Path

router = APIRouter()

# Setup logging
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S"
)

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
    try:
        # Ambil token dari query param
        token = websocket.query_params.get("token")
        logging.info(f"Incoming WebSocket connection with token: {token}")

        # Validasi token
        sessions = load_sessions()
        user_id = None
        for uid, sess in sessions.items():
            if sess.get("token") == token:
                user_id = uid
                break

        if not user_id:
            logging.warning("Connection rejected: invalid token")
            await websocket.close(code=1008)  # Policy Violation
            return

        # Terima koneksi
        await websocket.accept()
        active_connections[user_id] = websocket
        logging.info(f"User {user_id} connected. Active connections: {len(active_connections)}")

        while True:
            try:
                data = await websocket.receive_json()
                target_id = data.get("target_id")

                logging.info(f"Message from {user_id} → {target_id}: {data.get('type')}")

                if target_id in active_connections:
                    await active_connections[target_id].send_json({
                        "from": user_id,
                        "type": data.get("type"),
                        "sdp": data.get("sdp"),
                        "candidate": data.get("candidate")
                    })
                else:
                    logging.warning(f"Target {target_id} not connected")

            except Exception as e:
                logging.error(f"Error handling message from {user_id}: {e}")
                break

    except WebSocketDisconnect:
        logging.info(f"User {user_id} disconnected")
    except Exception as e:
        logging.error(f"Unexpected error: {e}")
    finally:
        if user_id in active_connections:
            del active_connections[user_id]
            logging.info(f"User {user_id} removed from active connections. Now: {len(active_connections)}")
