# Simpan OTP sementara (pakai dict)
otp_storage = {}

def save_otp(email: str, otp: str):
    otp_storage[email] = otp

def verify_otp(email: str, otp: str) -> bool:
    return otp_storage.get(email) == otp
