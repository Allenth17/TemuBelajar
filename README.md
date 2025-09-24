# TemuBelajar

TemuBelajar is a learning-partner matching and video chat application. The backend is built with FastAPI and provides:
- Email-based registration with OTP verification
- Login with email or username, and session tokens saved to sessions.json
- Profile retrieval using Bearer tokens
- Simple matchmaking and WebSocket signaling endpoints for peer-to-peer connections
- In-memory (JSON file-based) storage for users and sessions

This README describes how to set up and run the backend locally and outlines a project To-Do list.

Last updated: 2025-08-31 16:55 (local)


## Repository structure
- backend/ — FastAPI backend (primary focus of this README)
- frontend/ — Mobile/Desktop (Kotlin Multiplatform) and Web clients (under frontend/web)
- users.json, sessions.json, otp.json — local JSON storage used by the backend


## Requirements
- Python 3.10+
- An SMTP account (e.g., Gmail) for sending OTP codes
- PowerShell (on Windows) or a shell on other OSes


## Setup (Backend)
1. Create and activate a virtual environment (Windows PowerShell):
   - python -m venv .venv
   - .\.venv\Scripts\Activate.ps1
2. Install dependencies:
   - pip install fastapi uvicorn bcrypt python-dotenv
3. Create a .env file in the project root (C:\projek\TemuBelajar\.env) with:
   - SMTP_EMAIL="your_smtp_email@example.com"
   - SMTP_PASS="your_smtp_password_or_app_password"
   (Optional, if you later use JWT in config.py)
   - JWT_SECRET="some-long-secret"
   - JWT_ALGORITHM="HS256"
4. Ensure JSON storage files exist (they will be created if missing):
   - users.json
   - sessions.json
   - otp.json


## Run (Backend)
From the project root:
- uvicorn backend.app.main:app --reload --host 0.0.0.0 --port 8000

Test health:
- GET http://localhost:8000/ -> { "message": "backend is running" }


## Key Endpoints (summary)
- POST /register
  - body: { email, password, username, name, phone, university }
  - Sends OTP to the provided email if allowed domain.
- POST /verify-otp
  - body: { email, otp }
  - Verifies and marks user as verified.
- POST /resend-otp
  - body: { email }
  - Sends a fresh OTP if not yet verified.
- POST /login
  - body: { email_or_username, password }
  - Returns: { token, expires_in }
- GET /me
  - header: Authorization: Bearer <token>
  - Returns profile info if token valid.
- POST /logout
  - header: Authorization: Bearer <token>
  - Invalidates the session token.
- DELETE /expired-sessions
  - Removes expired sessions (server-side cleanup)
- WebSockets
  - /ws — matchmaking queue
  - /ws/signaling?token=... — signaling channel (token from /login). See backend/app/signaling.py
- Streaming helpers
  - POST /match — announce your stream and attempt to match another user
  - GET /get_stream/{user_id} — fetch a user stream record
  - POST /disconnect/{user_id} — remove a user from active streams
  - GET /streams — list all streams (from backend/app/stream_manager.py)


## Email/OTP configuration
- backend/app/email_utils.py loads .env from project root.
- Uses SMTP over SSL (smtp.gmail.com:465) by default. If using Gmail, create an App Password.
- Allowed domains configured in ALLOWED_DOMAINS within email_utils.py. Adjust to your needs.


## Maintenance scripts
- Stream cleanup: python -m backend.app.stream_cleanup
- OTP and unverified user cleanup: python -m backend.app.cleanup


## Notes
- Storage uses flat JSON files. See backend/app/main.py for read/write wrappers that keep a { "users": [...] } and { "sessions": [...] } structure.
- WebSocket signaling requires a valid token; sessions.json stores { token, email, expired_at }.
- CORS/middleware scaffolding exists under backend/app/middleware.


## To-Do
- Security and data
  - [x] Migrate from JSON files to a proper database (e.g., PostgreSQL/SQLite via SQLAlchemy)
  - [ ] Hash secret values and move all secrets to environment variables and/or a vault
  - [ ] Add password reset flow and email rate limiting
  - [ ] Implement account lockout/anti-bruteforce protections
- Email delivery
  - [ ] Add error handling and retries for SMTP failuresv
  - [ ] Support async mailers or a queue (e.g., Celery/RQ) to send OTPs
- API and validation
  - [ ] Expand OpenAPI docs and add examples/schemas for all endpoints
  - [ ] Add stricter validation for inputs and consistent error codes/messages
  - [ ] Add pagination/filtering where applicable
- WebSocket/signaling
  - [ ] Persist signaling/matchmaking state and add reconnection handling
  - [ ] Add authentication to the /ws endpoint (currently open)
  - [ ] Add presence/heartbeat and auto-cleanup for idle sockets
- Streaming
  - [ ] Replace JSON storage for streams with a store that supports TTL/expiry (Redis)
  - [ ] Add explicit status updates and events for stream lifecycle
- Observability
  - [ ] Structured logging and correlation IDs
  - [ ] Metrics and health checks (Prometheus endpoints)
  - [ ] Centralized error tracking (Sentry)
- Testing and quality
  - [ ] Unit and integration tests for all endpoints
  - [ ] WebSocket tests (connect, message exchange, disconnect)
  - [ ] Linting/formatting (ruff/black) and type checking (mypy)
- DevOps
  - [ ] Dockerfile and docker-compose for backend + dependencies
  - [ ] CI pipeline (GitHub Actions) for tests and linting
  - [ ] Environment-specific configs (dev/staging/prod)
- Frontend
  - [ ] Document how to run the Kotlin Multiplatform app
  - [ ] Document how to run the Web client (frontend/web/TemuBelajar)
  - [ ] Integrate with backend auth + signaling flows


## License
