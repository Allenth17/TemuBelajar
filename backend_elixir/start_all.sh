#!/usr/bin/env bash
# start_all.sh — starts all TemuBelajar backend services for local development
# Usage: ./start_all.sh
# Requires: elixir, mix installed. Optional: docker for PostgreSQL.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "╔══════════════════════════════════════════╗"
echo "║    TemuBelajar Backend — Starting All    ║"
echo "╚══════════════════════════════════════════╝"

# ── 1. Load environment variables from .env ─────────────────────────────────────
if [[ -f "$SCRIPT_DIR/.env" ]]; then
  echo "▶ Loading .env..."
  # Export each non-comment, non-empty line
  set -o allexport
  # shellcheck disable=SC1091
  source "$SCRIPT_DIR/.env"
  set +o allexport
  echo "✓ .env loaded"
fi

# ── 2. Optional: start all services via docker compose ─────────────────────────
if [[ "${USE_DOCKER:-false}" == "true" ]]; then
  echo ""
  echo "▶ Starting all services via Docker..."
  docker compose up --build -d
  echo "✓ All services started via Docker"
  echo ""
  echo "Services:"
  echo "  api_gateway   →  http://localhost:4000"
  echo "  auth_service  →  http://localhost:4001"
  echo "  user_service  →  http://localhost:4002"
  echo "  signaling     →  http://localhost:4003 (WS)"
  echo "  matchmaking   →  http://localhost:4004"
  echo "  email_service →  http://localhost:4005"
  echo "  social_service→  http://localhost:4006"
  echo ""
  echo "Health checks:"
  # Phase 8.4 — cap retries so a service that never becomes healthy
  # doesn't hang the script forever. 30 tries × 2s = ~60s per port.
  for port in 4000 4001 4002 4003 4004 4005 4006; do
    dc_retries=30
    until curl -sf "http://localhost:$port/api/health" > /dev/null 2>&1; do
      dc_retries=$((dc_retries - 1))
      if [[ $dc_retries -le 0 ]]; then
        echo "  ✗ port $port not healthy after 60s — aborting"
        exit 1
      fi
      sleep 2
    done
    echo "  ✓ port $port healthy"
  done
  exit 0
  fi

# ── 3. Helper: wait for a service health endpoint ────────────────────────────────
wait_for_service() {
  local name="$1"
  local port="$2"
  local url="http://localhost:$port/api/health"
  echo "  Waiting for $name (port $port)..."
  local retries=30
  until curl -sf "$url" > /dev/null 2>&1; do
    retries=$((retries - 1))
    if [[ $retries -le 0 ]]; then
      echo "  ✗ $name failed to start within 60s — aborting"
      exit 1
    fi
    sleep 2
  done
  echo "  ✓ $name is ready"
}

# ── 4. Start a single service in the background ──────────────────────────────────
start_service() {
  local SVC="$1"
  local PORT="$2"
  local SVC_DIR="$SCRIPT_DIR/services/$SVC"

  if [[ ! -d "$SVC_DIR" ]]; then
    echo "⚠ Service directory not found: $SVC_DIR — skipping"
    return
  fi

  echo "▶ Starting $SVC on port $PORT..."
  (
    cd "$SVC_DIR"
    # Install deps if vendor directory not yet populated
    if [[ ! -d deps ]]; then
      MIX_ENV=dev mix deps.get --only dev
    fi
    # Phase 8.3 — do NOT silence ecto.create / migrate errors. A broken
    # schema must surface as a clear non-zero exit here, not as a silent
    # 500 at request time. Services without Ecto report the task as
    # unknown and we let that propagate too (it's still a real signal).
    MIX_ENV=dev mix ecto.create --quiet
    MIX_ENV=dev mix ecto.migrate --quiet
    # Start the Phoenix server — PORT env var overrides dev.exs port
    MIX_ENV=dev PORT="$PORT" mix phx.server
  ) &
  PIDS+=($!)
}

PIDS=()

cleanup() {
  echo ""
  echo "Stopping all services..."
  for pid in "${PIDS[@]}"; do
    kill "$pid" 2>/dev/null || true
  done
  echo "✓ All stopped"
}
trap cleanup EXIT INT TERM

# ── 5. Start services in dependency order ────────────────────────────────────────
#
# Dependency graph:
#   auth_service (no deps)
#   email_service (no deps)
#   user_service (no deps)
#   matchmaking_service → auth_service
#   signaling_service   → auth_service, matchmaking_service
#   social_service (Ecto DB, no runtime service deps)
#   api_gateway         → all of the above

# Tier 1: services with no inter-service dependencies
start_service "auth_service"    "4001"
start_service "email_service"   "4005"
start_service "user_service"    "4002"
start_service "social_service"  "4006"

# Phase 8.5 — wait for ALL tier-1 services before starting the gateway.
# Previously only auth_service was awaited, so a failed user_service or
# social_service let the gateway come up healthy yet proxy to dead ports
# (503s in production). email_service has no /api/health dependency for
# the others but is awaited so the monolith sees a fully working stack.
wait_for_service "auth_service"   "4001"
wait_for_service "email_service"  "4005"
wait_for_service "user_service"    "4002"
wait_for_service "social_service" "4006"

# Tier 2: services that depend on auth_service
start_service "matchmaking_service" "4004"
start_service "signaling_service"   "4003"

# Wait for both before starting the gateway
wait_for_service "matchmaking_service" "4004"
wait_for_service "signaling_service"   "4003"

# Tier 3: API Gateway (depends on all services)
start_service "api_gateway" "4000"

echo ""
echo "✓ All services started:"
echo "  auth_service    →  http://localhost:4001"
echo "  email_service   →  http://localhost:4005"
echo "  user_service    →  http://localhost:4002"
echo "  social_service  →  http://localhost:4006"
echo "  matchmaking     →  http://localhost:4004"
echo "  signaling       →  http://localhost:4003 (WS: ws://localhost:4003)"
echo "  api_gateway     →  http://localhost:4000 (WS: ws://localhost:4000)"
echo ""
echo "Press Ctrl+C to stop all services."
wait
