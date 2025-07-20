from fastapi import Depends

def require_auth(request: Request):
    token = request.headers.get("Authorization")
    if not token:
        raise HTTPException(status_code=401, detail="Missing token")

    token = token.replace("Bearer ", "")
    with open("sessions.json", "r") as f:
        sessions = json.load(f)

    if token not in sessions:
        raise HTTPException(status_code=401, detail="Invalid or expired token")

    # bisa juga return user_id jika mau pakai nanti
    return sessions[token]["email"]
