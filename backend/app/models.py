from pydantic import BaseModel, EmailStr

class RegisterRequest(BaseModel):
    email: EmailStr

class RegisterResponse(BaseModel):
    message: str

class VerifyRequest(BaseModel):
    email: EmailStr
    otp: str

class VerifyResponse(BaseModel):
    message: str