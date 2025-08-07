from typing import List
from fastapi import WebSocket

waiting_users: List[WebSocket] = []

async def add_to_queue(ws: WebSocket):
    if ws not in waiting_users:
        waiting_users.append(ws)
        await try_match_users()

async def remove_from_queue(ws: WebSocket):
    if ws in waiting_users:
        waiting_users.remove(ws)

async def try_match_users():
    if len(waiting_users) >= 2:
        user1 = waiting_users.pop(0)
        user2 = waiting_users.pop(0)
        await user1.send_json({"type": "match_found", "role": "caller"})
        await user2.send_json({"type": "match_found", "role": "receiver"})
