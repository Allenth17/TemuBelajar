import json
import os
import time

OTP_PATH = "otp.json"
USER_PATH = "users.json"
EXPIRE_SECONDS = 300  # 5 menit

def load_json(path):
    if not os.path.exists(path):
        return [] if path.endswith("users.json") else {}
    with open(path, "r") as f:
        data = json.load(f)
    # unwrap for users.json if wrapped
    if path.endswith("users.json"):
        if isinstance(data, dict) and isinstance(data.get("users"), list):
            return data["users"]
        if isinstance(data, dict):
            return [{"email": email, **udata} for email, udata in data.items()]
        return data if isinstance(data, list) else []
    return data if isinstance(data, dict) else {}


def save_json(path, data):
    with open(path, "w") as f:
        if path.endswith("users.json"):
            json.dump({"users": data if isinstance(data, list) else []}, f, indent=2)
        else:
            json.dump(data, f, indent=2)


def cleanup_otp_and_users():
    otps = load_json(OTP_PATH)
    users = load_json(USER_PATH)
    current_time = int(time.time())
    
    for email in list(otps.keys()):
        otp_data = otps[email]
        if not otp_data.get("verified") and current_time - otp_data.get("timestamp", 0) > EXPIRE_SECONDS:
            print(f"[EXPIRED] Deleting unverified user: {email}")
            otps.pop(email)
            # remove from users list by email
            users = [u for u in users if u.get("email") != email]

    save_json(OTP_PATH, otps)
    save_json(USER_PATH, users)

if __name__ == "__main__":
    cleanup_otp_and_users()
