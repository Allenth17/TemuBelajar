import json
import os
import time

OTP_PATH = "otp.json"
USER_PATH = "users.json"
EXPIRE_SECONDS = 300  # 5 menit

def load_json(path):
    if not os.path.exists(path):
        return {}
    with open(path, "r") as f:
        return json.load(f)

def save_json(path, data):
    with open(path, "w") as f:
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
            users.pop(email, None)

    save_json(OTP_PATH, otps)
    save_json(USER_PATH, users)

if __name__ == "__main__":
    cleanup_otp_and_users()
