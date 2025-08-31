from typing import List, Dict
from fastapi import WebSocket
import uuid

waiting_users: List[WebSocket] = []
# Simple in-memory room mapping: pair_id -> tuple(WebSocket, WebSocket)
active_pairs: Dict[str, List[WebSocket]] = {}

async def add_to_queue(ws: WebSocket):
    if ws not in waiting_users:
        waiting_users.append(ws)
        await try_match_users()

async def remove_from_queue(ws: WebSocket):
    if ws in waiting_users:
        waiting_users.remove(ws)
    # Also remove from any active pair if present
    to_delete = []
    for pair_id, sockets in active_pairs.items():
        if ws in sockets:
            try:
                sockets.remove(ws)
            except ValueError:
                pass
            if not sockets:
                to_delete.append(pair_id)
    for pid in to_delete:
        active_pairs.pop(pid, None)

async def try_match_users():
    if len(waiting_users) >= 2:
        user1 = waiting_users.pop(0)
        user2 = waiting_users.pop(0)
        pair_id = str(uuid.uuid4())
        active_pairs[pair_id] = [user1, user2]
        await user1.send_json({"type": "match_found", "role": "caller", "pair_id": pair_id})
        await user2.send_json({"type": "match_found", "role": "receiver", "pair_id": pair_id})

