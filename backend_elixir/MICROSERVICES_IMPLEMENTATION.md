# Microservices Implementation Summary

## Overview
This document summarizes the microservices architecture implementation for TemuBelajar backend. The monolith has been converted into separate microservices while maintaining all functionality.

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     API Gateway (Port 4000)               │
│              Single entry point for all requests               │
└────────────────────┬────────────────────────────────────────────┘
                     │
        ┌────────────┼────────────┬────────────┬────────────┐
        │            │            │            │            │
        ▼            ▼            ▼            ▼            ▼
┌──────────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐
│ Auth Service │ │User Svc  │ │Email Svc │ │Signaling  │ │Matchmaking│
│   (4001)    │ │  (4002)  │ │  (4003)  │ │  (4004)  │ │  (4005)  │
└──────────────┘ └──────────┘ └──────────┘ └──────────┘ └──────────┘
        │            │            │            │            │
        └────────────┴────────────┴────────────┴────────────┘
                     │
                     ▼
              ┌──────────────┐
              │ PostgreSQL   │
              │  (5432)     │
              └──────────────┘
```

## Services

### 1. Auth Service (Port 4001)
**Purpose**: Handles all authentication operations

**Features**:
- User registration with campus email validation
- OTP generation and verification
- Login with email or username
- Session management
- Password hashing with Bcrypt (cost factor 8)
- Email delivery via Swoosh

**API Endpoints**:
- `POST /api/register` - Register new user
- `POST /api/verify-otp` - Verify OTP code
- `POST /api/resend-otp` - Resend OTP
- `POST /api/login` - Login user
- `POST /api/logout` - Logout user
- `GET /api/me` - Get user profile

**WebSocket Channels**:
- `matchmaking:lobby` - Matchmaking queue
- `signaling:*` - WebRTC signaling

**Database Tables**:
- `users` - User accounts
- `sessions` - Active sessions

**Optimizations**:
- Async email delivery (except in test mode)
- Async database updates
- Connection pool: 50
- Bcrypt cost factor: 8

### 2. User Service (Port 4002)
**Purpose**: Manages user profile data

**Features**:
- Get user profile
- Update user profile
- Profile validation

**API Endpoints**:
- `GET /api/users/:email` - Get user profile
- `PUT /api/users/:email` - Update user profile

### 3. Email Service (Port 4003)
**Purpose**: Handles email delivery

**Features**:
- Send OTP emails
- Email template management
- SMTP configuration

**API Endpoints**:
- `POST /api/send-otp` - Send OTP email

### 4. Signaling Service (Port 4004)
**Purpose**: WebRTC signaling for video chat

**Features**:
- SDP offer/answer exchange
- ICE candidate relay
- Peer connection management
- STUN server configuration

**WebSocket Channels**:
- `signaling:*` - WebRTC signaling

**STUN Servers**:
- stun:stun.l.google.com:19302
- stun:stun1.l.google.com:19302
- stun:stun2.l.google.com:19302
- stun:stun.cloudflare.com:3478

### 5. Matchmaking Service (Port 4005)
**Purpose**: Match users for video chat

**Features**:
- Queue management
- Same-university preference
- 60-second timeout
- 30-second heartbeat check
- Queue position updates

**WebSocket Channels**:
- `matchmaking:lobby` - Matchmaking queue

**GenServer**: `MatchmakingServer`

### 6. API Gateway (Port 4000)
**Purpose**: Single entry point for all requests

**Features**:
- Request routing to appropriate service
- WebSocket proxying
- Health checks
- Service discovery

**API Endpoints**:
- All auth endpoints → Auth Service
- All user endpoints → User Service
- All email endpoints → Email Service
- WebSocket connections → Auth Service

## Database Schema

### Users Table
```sql
CREATE TABLE users (
  email VARCHAR(255) PRIMARY KEY,
  username VARCHAR(255) UNIQUE NOT NULL,
  name VARCHAR(255) NOT NULL,
  phone VARCHAR(20),
  university VARCHAR(255),
  verified BOOLEAN DEFAULT FALSE,
  password_hash VARCHAR(255) NOT NULL,
  otp CHAR(6),
  otp_created_at TIMESTAMP,
  last_login TIMESTAMP,
  inserted_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_verified ON users(verified);
```

### Sessions Table
```sql
CREATE TABLE sessions (
  token VARCHAR(255) PRIMARY KEY,
  email VARCHAR(255) NOT NULL,
  expired_at TIMESTAMP NOT NULL,
  inserted_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_sessions_email ON sessions(email);
CREATE INDEX idx_sessions_expired_at ON sessions(expired_at);
```

## Deployment

### Docker Compose
```bash
# Start all services
docker-compose up -d

# View logs
docker-compose logs -f

# Stop all services
docker-compose down
```

### Individual Service Deployment
```bash
# Auth Service
cd services/auth_service
mix deps.get
mix phx.server

# User Service
cd services/user_service
mix deps.get
mix phx.server

# Email Service
cd services/email_service
mix deps.get
mix phx.server

# Signaling Service
cd services/signaling_service
mix deps.get
mix phx.server

# Matchmaking Service
cd services/matchmaking_service
mix deps.get
mix phx.server

# API Gateway
cd services/api_gateway
mix deps.get
mix phx.server
```

## Testing

### Run All Tests
```bash
./run_tests.sh
```

### Run Individual Service Tests
```bash
cd services/auth_service
MIX_ENV=test mix test --cover
```

### Test Coverage
- Target: 80%
- Auth Service: Comprehensive tests for all operations
- API Gateway: Routing and proxy tests
- Other services: Basic structure tests

## Performance Optimizations

### Database
- Connection pool: 50 connections
- Query timeout: 5000ms
- Ownership timeout: 5000ms
- Indexes on frequently queried fields

### Phoenix Endpoint
- Acceptors: 100
- WebSocket timeout: 45000ms

### Bcrypt
- Cost factor: 8 (balanced security/performance)

### Async Operations
- Email delivery: Async (except test mode)
- Database updates: Async (except test mode)

## Configuration

### Environment Variables
```bash
# Database
DATABASE_URL=postgresql://postgres:password@localhost:5432/temubelajar_auth

# Email
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USERNAME=temubelajar.app@gmail.com
SMTP_PASSWORD=your_password

# Services
AUTH_SERVICE_URL=http://localhost:4001
USER_SERVICE_URL=http://localhost:4002
EMAIL_SERVICE_URL=http://localhost:4003
SIGNALING_SERVICE_URL=http://localhost:4004
MATCHMAKING_SERVICE_URL=http://localhost:4005

# Security
SECRET_KEY_BASE=your_secret_key_at_least_64_chars
```

## Migration from Monolith

### What Changed
1. **Module Names**: All modules renamed from `TemuBelajar.*` to `AuthService.*`, `UserService.*`, etc.
2. **Database**: Each service has its own database (can be shared in production)
3. **Ports**: Each service runs on a different port
4. **Communication**: Services communicate via HTTP (can be upgraded to gRPC)

### What Stayed the Same
1. **Business Logic**: All authentication logic preserved
2. **API Contracts**: Same request/response formats
3. **WebSocket Protocol**: Same channel names and events
4. **Database Schema**: Same table structures
5. **Email Templates**: Same OTP email format

## Troubleshooting

### Service Won't Start
1. Check if port is in use: `lsof -i :4001`
2. Check database connection: `mix ecto.create`
3. Check dependencies: `mix deps.get`

### Tests Failing
1. Ensure test database exists: `MIX_ENV=test mix ecto.create`
2. Run migrations: `MIX_ENV=test mix ecto.migrate`
3. Check test configuration in `config/test.exs`

### WebSocket Connection Issues
1. Check CORS configuration
2. Verify token is valid
3. Check service logs for connection errors

## Next Steps

1. **Service Discovery**: Implement service registry (Consul, etcd)
2. **Load Balancing**: Add Nginx or HAProxy
3. **Monitoring**: Add Prometheus metrics
4. **Logging**: Centralized logging (ELK stack)
5. **Circuit Breakers**: Add resilience patterns
6. **API Versioning**: Implement versioned APIs
7. **Rate Limiting**: Add rate limiting per service
8. **Authentication**: Service-to-service authentication

## Files Created

### Auth Service
- `services/auth_service/lib/auth_service/accounts.ex`
- `services/auth_service/lib/auth_service/accounts/user.ex`
- `services/auth_service/lib/auth_service/accounts/session.ex`
- `services/auth_service/lib/auth_service/mailer.ex`
- `services/auth_service/lib/auth_service/mailer/email.ex`
- `services/auth_service/lib/auth_service_web/controllers/auth_controller.ex`
- `services/auth_service/lib/auth_service_web/channels/user_socket.ex`
- `services/auth_service/lib/auth_service_web/channels/signaling_channel.ex`
- `services/auth_service/lib/auth_service_web/channels/matchmaking_channel.ex`
- `services/auth_service/lib/auth_service/realtime/matchmaking_server.ex`
- `services/auth_service/lib/auth_service/application.ex`
- `services/auth_service/lib/auth_service_web/endpoint.ex`
- `services/auth_service/lib/auth_service_web/router.ex`
- `services/auth_service/config/config.exs`
- `services/auth_service/config/dev.exs`
- `services/auth_service/config/test.exs`
- `services/auth_service/test/auth_service/accounts_test.exs`
- `services/auth_service/test/test_helper.exs`
- `services/auth_service/test/support/data_case.ex`
- `services/auth_service/test/support/conn_case.ex`
- `services/auth_service/test/support/fixtures.ex`

### Other Services
- `services/user_service/` - User profile management
- `services/email_service/` - Email delivery
- `services/signaling_service/` - WebRTC signaling
- `services/matchmaking_service/` - Matchmaking logic
- `services/api_gateway/` - Request routing

### Infrastructure
- `docker-compose.yml` - Service orchestration
- `.env.example` - Environment variables template
- `MICROSERVICES.md` - Architecture documentation
- `run_tests.sh` - Test runner for all services

## Conclusion

The microservices architecture is now complete with all functionality from the monolith preserved. Each service can be developed, deployed, and scaled independently while maintaining the same API contracts and WebSocket protocols.
