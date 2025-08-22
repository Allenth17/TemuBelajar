from fastapi import WebSocket
from typing import Dict

class SignalingManager:
    def __init__(self):
        self.connections: Dict[str, WebSocket] = {}

    async def connect(self, user_id: str, websocket: WebSocket):
        await websocket.accept()
        self.connections[user_id] = websocket
        try:
            while True:
                data = await websocket.receive_json()
                target_id = data.get("target_id")
                message = data.get("message")
                if target_id in self.connections:
                    await self.connections[target_id].send_json({
                        "from": user_id,
                        "message": message
                    })
        except:
            await self.disconnect(user_id)

    async def disconnect(self, user_id: str):
        if user_id in self.connections:
            del self.connections[user_id]

signaling_manager = SignalingManager()
