from sqlalchemy import Column, String, Boolean, DateTime, Integer
from sqlalchemy.ext.declarative import declarative_base

Base = declarative_base()

class User(Base):
    __tablename__ = "users"
    email = Column(String, primary_key=True)
    otp = Column(String)
    verified = Column(Boolean)
    created_at = Column(DateTime)
    password = Column(String)
    name = Column(String)
    phone = Column(String)
    university = Column(String)
    username = Column(String)
    last_login = Column(DateTime, nullable=True)

class Session(Base):
    __tablename__ = "sessions"
    token = Column(String, primary_key=True)
    email = Column(String)
    expired_at = Column(DateTime)

class OTP(Base):
    __tablename__ = "otps"
    email = Column(String, primary_key=True)
    otp = Column(String)
    timestamp = Column(Integer)
    verified = Column(Boolean)