from functools import lru_cache
from pydantic.v1 import BaseSettings


class Settings(BaseSettings):
    smtp_email: str
    smtp_pass: str
    jwt_secret: str
    jwt_algorithm: str = "HS256"
    
    class Config:
        env_file = ".env"

@lru_cache()
def get_settings():
    return Settings()

