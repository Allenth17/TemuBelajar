from pydantic import BaseModel, EmailStr

class RegisterRequest(BaseModel):
    email: EmailStr

class RegisterResponse(BaseModel):
    success: bool
    message: str