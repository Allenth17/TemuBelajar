import json
from pathlib import Path
import os
import json
from datetime import datetime, timedelta, timezone


STREAM_FILE = Path(__file__).resolve().parent / "active_streams.json"


def load_streams():
    if not STREAM_FILE.exists():
        with open(STREAM_FILE, "w") as f:
            json.dump({}, f)
    with open(STREAM_FILE) as f:
        return json.load(f)

def save_streams(data):
    with open(STREAM_FILE, "w") as f:
        json.dump(data, f, indent=4)

def add_stream(user_id: str, stream_url: str):
    data = load_streams()
    data[user_id] = {
        "stream_url": stream_url,
        "last_seen": datetime.now(timezone.utc).isoformat()
    }
    save_streams(data)


def remove_stream(user_id: str) -> bool:
    data = load_streams()
    removed = False
    if user_id in data:
        del data[user_id]
        removed = True
    save_streams(data)
    return removed

def get_stream(user_id: str):
    data = load_streams()
    return data.get(user_id)

def update_stream_status(user_id: str, status: str) -> bool:
    """Update stream status for a user"""
    data = load_streams()
    if user_id not in data:
        return False
        
    data[user_id]["status"] = status
    data[user_id]["updated_at"] = datetime.now(timezone.utc).isoformat()
    save_streams(data)
    return True

def get_active_streams() -> dict:
    """Get all active streams"""
    data = load_streams()
    return {
        uid: info for uid, info in data.items() 
        if info.get("status") == "active"
    }


def cleanup_streams(timeout_minutes: int = 5):
    if not os.path.exists(STREAM_FILE):
        return

    with open(STREAM_FILE, "r") as f:
        data = json.load(f)

    now = datetime.utcnow()
    cleaned_data = {}

    for user_id, stream_info in data.items():
        last_seen_str = stream_info.get("last_seen")
        if not last_seen_str:
            continue
        last_seen = datetime.fromisoformat(last_seen_str)
        if now - last_seen < timedelta(minutes=timeout_minutes):
            cleaned_data[user_id] = stream_info

    with open(STREAM_FILE, "w") as f:
        json.dump(cleaned_data, f, indent=4)