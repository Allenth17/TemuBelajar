import json
import asyncio
from datetime import datetime
from backend.app.db_models import Base, User, Session, OTP
from backend.app.database import engine, AsyncSessionLocal

async def migrate():
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)

    # Users
    with open("users.json") as f:
        users_data = json.load(f)["users"]
    async with AsyncSessionLocal() as session:
        for u in users_data:
            user = User(
                email=u["email"],
                otp=u.get("otp"),
                verified=u.get("verified", False),
                created_at=datetime.fromisoformat(u["created_at"]),
                password=u["password"],
                name=u.get("name"),
                phone=u.get("phone"),
                university=u.get("university"),
                username=u.get("username"),
                last_login=datetime.fromisoformat(u["last_login"]) if u.get("last_login") else None
            )
            session.add(user)
        await session.commit()

    # Sessions
    with open("sessions.json") as f:
        sessions_data = json.load(f)["sessions"]
    async with AsyncSessionLocal() as session:
        for s in sessions_data:
            session_obj = Session(
                token=s["token"],
                email=s["email"],
                expired_at=datetime.fromisoformat(s["expired_at"])
            )
            session.add(session_obj)
        await session.commit()

    # OTPs (if any)
    try:
        with open("otp.json") as f:
            otps_data = json.load(f)
        async with AsyncSessionLocal() as session:
            for email, o in otps_data.items():
                otp = OTP(
                    email=email,
                    otp=o["otp"],
                    timestamp=o["timestamp"],
                    verified=o.get("verified", False)
                )
                session.add(otp)
            await session.commit()
    except Exception:
        pass

if __name__ == "__main__":
    asyncio.run(migrate())