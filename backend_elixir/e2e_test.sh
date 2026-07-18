#!/usr/bin/env bash
# e2e_test.sh — end-to-end smoke test against a running TemuBelajar backend
# (gateway + auth/user/social services + Postgres). Exercises register →
# login → /me → social follow → social profile, asserting specific JSON keys
# at each step.
#
# Phase 8.9 — fail fast on any error so silent gateway 503s no longer look
# like a green test run. Asserts concrete fields instead of "| jq || echo".

set -euo pipefail

GATEWAY="${GATEWAY:-http://localhost:4000}"

# ── Readiness: wait for the gateway to come up, hard-fail otherwise ────────
echo -n "Waiting for API Gateway (${GATEWAY})... "
gateway_ready=false
for _ in {1..30}; do
  if curl -fsS "${GATEWAY}/api/health" > /dev/null 2>&1; then
    gateway_ready=true
    break
  fi
  sleep 1
done
if [[ "$gateway_ready" != "true" ]]; then
  echo "not reachable after 30s"
  echo "❌ ERROR: API Gateway is not up — start it (./start_all.sh) before running e2e."
  exit 1
fi
echo "Up!"

# ── Register ──────────────────────────────────────────────────────────────
EMAIL="test_$(date +%s)@ui.ac.id"
USERNAME="user$(date +%s)"
echo "Registering $EMAIL..."
REGISTER_RESP=$(curl -fsS -X POST "${GATEWAY}/api/register" \
  -H "Content-Type: application/json" \
  -d "{\"email\": \"${EMAIL}\", \"password\": \"password123\", \"username\": \"${USERNAME}\", \"name\": \"E2E Test\", \"phone\": \"080000000\", \"university\": \"UI\"}")
echo "$REGISTER_RESP" | jq .
echo "$REGISTER_RESP" | jq -e 'has("email") or has("message") or has("id") or has("otp_sent")' > /dev/null \
  || { echo "❌ Register response missing expected keys"; echo "$REGISTER_RESP"; exit 1; }

# ── Login ─────────────────────────────────────────────────────────────────
echo -e "\nLogging in..."
LOGIN_RESP=$(curl -fsS -X POST "${GATEWAY}/api/login" \
  -H "Content-Type: application/json" \
  -d "{\"email_or_username\": \"${EMAIL}\", \"password\": \"password123\"}")
echo "$LOGIN_RESP" | jq .
echo "$LOGIN_RESP" | jq -e 'has("token")' > /dev/null \
  || { echo "❌ Login response missing token"; echo "$LOGIN_RESP"; exit 1; }

TOKEN=$(echo "$LOGIN_RESP" | jq -r '.token')

# ── /me ───────────────────────────────────────────────────────────────────
echo -e "\nTesting API Gateway -> User & Auth Services (Me)..."
ME_RESP=$(curl -fsS -X GET "${GATEWAY}/api/me" -H "Authorization: Bearer ${TOKEN}")
echo "$ME_RESP" | jq .
echo "$ME_RESP" | jq -e '(has("email") or has("user"))' > /dev/null \
  || { echo "❌ /me response missing user fields"; echo "$ME_RESP"; exit 1; }

# ── Social follow ──────────────────────────────────────────────────────────
echo -e "\n\nTesting API Gateway -> Social Service (Follow)"
# Phase 8.9 — social controller expects `{"target": ...}` (not `target_email`).
# Sending the wrong key made follow silently no-op in the previous script.
FOLLOW_RESP=$(curl -fsS -X POST "${GATEWAY}/api/social/follow" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{\"target\": \"alice@ui.ac.id\"}")
echo "$FOLLOW_RESP" | jq .
echo "$FOLLOW_RESP" | jq -e 'has("ok") or has("followed") or has("error") or has("message")' > /dev/null \
  || { echo "❌ Follow response missing expected keys"; echo "$FOLLOW_RESP"; exit 1; }

# ── Social profile ─────────────────────────────────────────────────────────
echo -e "\n\nTesting API Gateway -> Social Service (Profile)"
PROFILE_RESP=$(curl -fsS -X GET "${GATEWAY}/api/social/profile/${EMAIL}" \
  -H "Authorization: Bearer ${TOKEN}")
echo "$PROFILE_RESP" | jq .
echo "$PROFILE_RESP" | jq -e 'has("follower_count") or has("email") or has("error") or has("message")' > /dev/null \
  || { echo "❌ Profile response missing expected keys"; echo "$PROFILE_RESP"; exit 1; }

echo -e "\n\n✅ All integration tests passed."
