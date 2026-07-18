#!/usr/bin/env bash

# run_tests.sh
# Shell script to run all unit, api, genserver, and websocket tests for all microservices.

# Phase 8.1 — replaced hardcoded absolute cd with a path relative to the
# script's own location so this works on any machine / CI runner.
# Phase 8.2 — fail fast on the first error (deps.get, compile, or test),
# on undefined variables, and on any command in a pipe that fails.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "================================================="
echo "   TemuBelajar Elixir Backend Test Runner"
echo "   Microservices Architecture"
echo "================================================="

# Track overall success. set -e will exit on the first hard failure, but
# we want to keep running other services' tests so we capture per-service
# outcomes and surface a non-zero exit at the very end.
OVERALL_SUCCESS=0

# Phase 8.8 — DB readiness check. The `mix test` alias in each Ecto service
# starts with `ecto.create --quiet + ecto.migrate --quiet`; if Postgres is
# still booting the first `mix test` will race and produce confusing errors.
# We probe Postgres once up-front and bail with a clear message instead of
# racing every service.
wait_for_postgres() {
  local pg_host="${POSTGRES_HOST:-localhost}"
  local pg_port="${POSTGRES_PORT:-5432}"
  local retries=30
  echo "=> Waiting for PostgreSQL at ${pg_host}:${pg_port}..."
  until (echo > /dev/tcp/"${pg_host}"/"${pg_port}") 2>/dev/null || pg_isready -h "${pg_host}" -p "${pg_port}" >/dev/null 2>&1; do
    retries=$((retries - 1))
    if [[ $retries -le 0 ]]; then
      echo "❌ ERROR: PostgreSQL not reachable at ${pg_host}:${pg_port} after 60s — stub services that need it will fail."
      echo "   Start Postgres (e.g. docker compose up -d postgres) and re-run."
      exit 1
    fi
    sleep 2
  done
  echo "✓ PostgreSQL is reachable"
}

# Only run the DB check if Postgres is expected (some services don't use Ecto,
# but the monolith + auth/user/social do). Skipping when POSTGRES_SKIP=1 lets
# CI run pure-proxy services without a DB.
if [[ "${POSTGRES_SKIP:-0}" != "1" ]]; then
  wait_for_postgres
fi

# Function to run tests for a service
run_service_tests() {
    local service_name=$1
    local service_path=$2

    echo ""
    echo "---------------------------------------------------"
    echo "Testing $service_name"
    echo "---------------------------------------------------"

    if [[ ! -d "$service_path" ]]; then
        echo "⚠️  WARNING: $service_name directory not found, skipping..."
        return 0
    fi

    # Run the service's tests inside a subshell so a failure doesn't kill the
    # whole run before other services have had a chance to report. $OVERALL_SUCCESS
    # is inherited/exported via the surrounding script's scope.
    (
      set -euo pipefail
      cd "$service_path"

      # Ensure dependencies are available and compiled for test
      echo "=> Setting up test environment for $service_name..."
      export MIX_ENV=test
      mix deps.get

      echo "=> Running test suite for $service_name..."
      mix test --cover
    ) || {
        echo "❌ ERROR: $service_name tests failed!"
        OVERALL_SUCCESS=1
        return 0
    }

    echo "✅ SUCCESS: $service_name tests passed!"
}

# Test all microservices
run_service_tests "Auth Service" "services/auth_service"
run_service_tests "User Service" "services/user_service"
run_service_tests "Email Service" "services/email_service"
run_service_tests "Signaling Service" "services/signaling_service"
run_service_tests "Matchmaking Service" "services/matchmaking_service"
run_service_tests "Social Service" "services/social_service"
run_service_tests "API Gateway" "services/api_gateway"

# Also test the monolith (for backward compatibility)
echo ""
echo "---------------------------------------------------"
echo "Testing Monolith (for backward compatibility)"
echo "---------------------------------------------------"
(
  set -euo pipefail
  export MIX_ENV=test
  mix deps.get
  mix test --cover
) || OVERALL_SUCCESS=1

echo ""
echo "================================================="
if [[ $OVERALL_SUCCESS -eq 0 ]]; then
    echo "✅ SUCCESS: All tests executed successfully!"
    echo "================================================="
    exit 0
else
    echo "❌ ERROR: One or more tests failed. Check logs."
    echo "================================================="
    exit 1
fi
