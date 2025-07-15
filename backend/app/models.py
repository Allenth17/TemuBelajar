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
    
class OtpVerificationRequest(BaseModel):
    email: EmailStr
    otp: str

class LoginRequest(BaseModel):
    email: EmailStr
    otp: str
    
class EmailRequest(BaseModel):
    email: EmailStr