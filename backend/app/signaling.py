from fastapi import APIRouter, WebSocket, WebSocketDisconnect
from typing import Dict

router = APIRouter()

active_connections: Dict[str, WebSocket] = {}

@router.websocket("/ws/{user_id}")
async def websocket_endpoint(websocket: WebSocket, user_id: str):
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
        del active_connections[user_id]
