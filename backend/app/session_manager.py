from datetime import datetime, timedelta
import json
from typing import Optional

class SessionManager:
    def __init__(self, session_file: str = "sessions.json"):
        self.session_file = session_file
        
    def validate_token(self, token: str) -> Optional[str]:
        """Validate token and return user email if valid"""
        sessions = self._load_sessions()
        if token not in sessions:
            return None
            
        session = sessions[token]
        if datetime.fromisoformat(session["expired_at"]) < datetime.utcnow():
            return None
            
        return session["email"]
        
    def _load_sessions(self) -> dict:
        try:
            with open(self.session_file, "r") as f:
                return json.load(f)
        except FileNotFoundError:
            return {}
