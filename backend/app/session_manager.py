from datetime import datetime
import json
from typing import Optional

class SessionManager:
    def __init__(self, session_file: str = "sessions.json"):
        self.session_file = session_file
        
    def validate_token(self, token: str) -> Optional[str]:
        """Validate token and return user email if valid"""
        sessions = self._load_sessions()
        session = next((s for s in sessions if s.get("token") == token), None)
        if not session:
            return None
        if datetime.fromisoformat(session["expired_at"]) < datetime.utcnow():
            return None
        return session["email"]
        
    def _load_sessions(self) -> list:
        try:
            with open(self.session_file, "r") as f:
                data = json.load(f)
            # New format: { "sessions": [ ... ] }
            if isinstance(data, dict) and isinstance(data.get("sessions"), list):
                return data["sessions"]
            # Old flat dict
            if isinstance(data, dict):
                return [{"token": token, **sdata} for token, sdata in data.items()]
            # Already a list
            if isinstance(data, list):
                return data
            return []
        except FileNotFoundError:
            return []
