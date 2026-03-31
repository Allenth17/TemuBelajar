# TemuBelajar Microservices Architecture

## Overview

This document describes the microservices architecture for the TemuBelajar application. The monolith backend has been converted into separate, independently deployable services for better maintainability and scalability.

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                     API Gateway (Port 4000)                  │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │ Routes all requests to appropriate services          │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘
         │                    │                    │
         ▼                    ▼                    ▼
┌──────────────┐   ┌──────────────┐   ┌──────────────┐
│ Auth Service │   │ User Service │   │Email Service │
│   Port 4001  │   │   Port 4002  │   │  Port 4005  │
└──────────────┘   └──────────────┘   └──────────────┘
         │                    │
         ▼                    ▼
┌──────────────────────────────────────────────────────────────┐
│              PostgreSQL Database (Port 5432)              │
│  - temubelajar_auth (Auth Service)                   │
│  - temubelajar_user (User Service)                   │
└──────────────────────────────────────────────────────────────┘
         │
         ▼
┌──────────────┐   ┌──────────────┐
│Signaling Svc│   │Matchmaking   │
│  Port 4003  │   │  Port 4004   │
└──────────────┘   └──────────────┘
```

## Services

### 1. API Gateway (Port 4000)
**Purpose**: Single entry point for all client requests. Routes requests to appropriate backend services.

**Responsibilities**:
- Request routing and proxying
- CORS handling
- Service discovery

**Endpoints**:
- `POST /api/register` → Auth Service
- `POST /api/verify-otp` → Auth Service
- `POST /api/login` → Auth Service
- `POST /api/logout` → Auth Service
- `GET /api/me` → Auth Service
- `GET /api/user/:email` → User Service
- `PUT /api/user/:email` → User Service
- `GET /api/users` → User Service
- `GET /api/users/search` → User Service
- `POST /api/signaling/join` → Signaling Service
- `POST /api/signaling/offer` → Signaling Service
- `POST /api/signaling/answer` → Signaling Service
- `POST /api/signaling/ice` → Signaling Service
- `POST /api/matchmaking/join` → Matchmaking Service
- `POST /api/matchmaking/leave` → Matchmaking Service

### 2. Auth Service (Port 4001)
**Purpose**: Handles user authentication and authorization.

**Responsibilities**:
- User registration
- OTP generation and verification
- Login/logout
- Session management
- Password hashing with Bcrypt

**Database**: `temubelajar_auth`
- Tables: `users`, `sessions`

**Dependencies**:
- Email Service (for sending OTP emails)

### 3. User Service (Port 4002)
**Purpose**: Manages user profile data.

**Responsibilities**:
- Get user profile
- Update user profile
- List users
- Search users

**Database**: `temubelajar_user`
- Tables: `users`

### 4. Email Service (Port 4005)
**Purpose**: Handles email delivery.

**Responsibilities**:
- Send OTP emails
- Email template management

**Dependencies**:
- SMTP server (configured via environment variables)

### 5. Signaling Service (Port 4003)
**Purpose**: WebRTC signaling for peer-to-peer video chat.

**Responsibilities**:
- WebSocket connections for video chat
- SDP offer/answer exchange
- ICE candidate exchange
- Heartbeat monitoring
- Peer disconnect handling

**WebSocket Channel**: `signaling:{pair_id}`

### 6. Matchmaking Service (Port 4004)
**Purpose**: Matches users for video chat sessions.

**Responsibilities**:
- Queue management
- User matching logic
- Queue timeout handling
- Heartbeat monitoring

**WebSocket Channel**: `matchmaking:queue`

## Database Schema

### Users Table
```sql
CREATE TABLE users (
  email VARCHAR PRIMARY KEY,
  name VARCHAR NOT NULL,
  username VARCHAR NOT NULL UNIQUE,
  phone VARCHAR,
  university VARCHAR,
  verified BOOLEAN DEFAULT FALSE,
  password_hash VARCHAR NOT NULL,
  otp VARCHAR,
  otp_created_at TIMESTAMP,
  last_login TIMESTAMP,
  inserted_at TIMESTAMP DEFAULT NOW(),
  updated_at TIMESTAMP DEFAULT NOW()
);

-- Indexes
CREATE INDEX users_username_index ON users (username);
CREATE INDEX users_verified_index ON users (verified);
CREATE INDEX users_otp_created_at_index ON users (otp_created_at);
CREATE INDEX users_last_login_index ON users (last_login);
CREATE INDEX users_verified_otp_created_at_index ON users (verified, otp_created_at);
```

### Sessions Table
```sql
CREATE TABLE sessions (
  token VARCHAR PRIMARY KEY,
  email VARCHAR NOT NULL,
  expired_at TIMESTAMP NOT NULL,
  inserted_at TIMESTAMP DEFAULT NOW()
);

-- Indexes
CREATE INDEX sessions_email_index ON sessions (email);
CREATE INDEX sessions_expired_at_index ON sessions (expired_at);
```

## Deployment

### Using Docker Compose

1. Copy `.env.example` to `.env` and configure:
   ```bash
   cp .env.example .env
   # Edit .env with your configuration
   ```

2. Start all services:
   ```bash
   docker-compose up -d
   ```

3. Check service health:
   ```bash
   # API Gateway
   curl http://localhost:4000/api/health
   
   # Auth Service
   curl http://localhost:4001/api/health
   
   # User Service
   curl http://localhost:4002/api/health
   
   # Email Service
   curl http://localhost:4005/api/health
   
   # Signaling Service
   curl http://localhost:4003/api/health
   
   # Matchmaking Service
   curl http://localhost:4004/api/health
   ```

4. View logs:
   ```bash
   docker-compose logs -f
   ```

5. Stop all services:
   ```bash
   docker-compose down
   ```

### Individual Service Deployment

Each service can be deployed independently:

```bash
# Build and run Auth Service
cd services/auth_service
mix deps.get
mix compile
mix phx.server

# Build and run User Service
cd services/user_service
mix deps.get
mix compile
mix phx.server

# ... and so on for other services
```

## Environment Variables

### Database
- `DATABASE_URL`: PostgreSQL connection string
- `POOL_SIZE`: Database connection pool size (default: 50)

### Email
- `SMTP_EMAIL`: SMTP username
- `SMTP_PASS`: SMTP password

### Services
- `AUTH_SERVICE_URL`: Auth service URL
- `USER_SERVICE_URL`: User service URL
- `EMAIL_SERVICE_URL`: Email service URL
- `SIGNALING_SERVICE_URL`: Signaling service URL
- `MATCHMAKING_SERVICE_URL`: Matchmaking service URL

### Security
- `SECRET_KEY_BASE`: Secret key for Phoenix (64+ characters)

## Performance Optimizations

All services are optimized for high performance:

1. **Database Connection Pooling**: Pool size of 50 connections
2. **Phoenix Configuration**: 100 acceptors for HTTP server
3. **Asynchronous Operations**: Non-blocking email delivery and database updates
4. **Database Indexes**: Optimized indexes for frequently queried fields
5. **Bcrypt Cost Factor**: Reduced to 8 for faster password hashing

## Testing

Run tests for all services:

```bash
# Test Auth Service
cd services/auth_service
mix test

# Test User Service
cd services/user_service
mix test

# Test Email Service
cd services/email_service
mix test

# Test Signaling Service
cd services/signaling_service
mix test

# Test Matchmaking Service
cd services/matchmaking_service
mix test

# Test API Gateway
cd services/api_gateway
mix test
```

## Maintenance Benefits

1. **Independent Deployment**: Each service can be updated without affecting others
2. **Isolated Failures**: Failure in one service doesn't bring down the entire system
3. **Scalability**: Each service can be scaled independently based on load
4. **Technology Flexibility**: Different services can use different technologies if needed
5. **Team Autonomy**: Different teams can work on different services simultaneously

## Migration from Monolith

The original monolith application is preserved in the root directory. To migrate:

1. Deploy microservices alongside the monolith
2. Gradually move traffic to the API Gateway
3. Monitor performance and stability
4. Decommission monolith endpoints as they're migrated

## Troubleshooting

### Service won't start
- Check if port is already in use: `lsof -i :4000`
- Check database connection: Verify DATABASE_URL is correct
- Check logs: `docker-compose logs <service_name>`

### Database connection errors
- Ensure PostgreSQL is running: `docker-compose ps postgres`
- Check database credentials in .env
- Verify database exists: Connect with psql and list databases

### Email not sending
- Verify SMTP credentials
- Check if SMTP port (587) is accessible
- Review Email Service logs

### WebSocket connection issues
- Check Signaling Service is running
- Verify WebSocket URL includes correct port (4003)
- Check firewall rules for WebSocket connections

## Future Enhancements

1. **Service Discovery**: Implement dynamic service discovery (e.g., Consul, etcd)
2. **Load Balancing**: Add load balancer for each service
3. **Monitoring**: Implement centralized logging and monitoring (e.g., Prometheus, Grafana)
4. **Circuit Breakers**: Add circuit breakers for service-to-service communication
5. **API Versioning**: Implement versioned APIs for backward compatibility
6. **Rate Limiting**: Add rate limiting at the API Gateway level
