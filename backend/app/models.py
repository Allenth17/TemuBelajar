from pydantic import BaseModel, EmailStr, validator

class RegisterRequest(BaseModel):
    email: str
    password: str
    username: str
    name: str
    phone: str
    university: str

    @validator("password")
    def validate_password(cls, v):
        if len(v) < 8:
            raise ValueError("Password must be at least 8 characters long")
        if not any(c.isdigit() for c in v):
            raise ValueError("Password must contain at least one number")
        if not any(c.isalpha() for c in v):
            raise ValueError("Password must contain at least one letter")
        return v
    
    @validator("username")
    def validate_username(cls, v):
        if not v.isalnum():
            raise ValueError("Username must be alphanumeric")
        if len(v) < 3 or len(v) > 20:
            raise ValueError("Username must be between 3 and 20 characters long")
        return v
    
class RegisterResponse(BaseModel):
    message: str

class VerifyRequest(BaseModel):
    email: str
    otp: str

class VerifyResponse(BaseModel):
    message: str
    
class OtpVerificationRequest(BaseModel):
    email: EmailStr
    otp: str

class LoginRequest(BaseModel):
    email_or_username: str
    password: str

    
class EmailRequest(BaseModel):
    email: str

class MatchRequest(BaseModel):
    user_id: str
    stream_url: str