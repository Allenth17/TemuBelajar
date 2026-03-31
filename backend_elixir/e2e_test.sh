#!/bin/bash
echo -n "Waiting for API Gateway... "
for i in {1..20}; do
  if curl -s http://localhost:4000/api/health > /dev/null; then
    echo "Up!"
    break
  fi
  sleep 1
done

echo "Testing API Gateway -> Auth Service (Register & Login)"
EMAIL="test_$(date +%s)@ui.ac.id"
echo "Registering $EMAIL..."
curl -s -X POST http://localhost:4000/api/register \
  -H "Content-Type: application/json" \
  -d "{\"email\": \"$EMAIL\", \"password\": \"password123\", \"username\": \"user$(date +%s)\", \"name\": \"E2E Test\", \"phone\": \"080000000\", \"university\": \"UI\"}" | jq || echo "Register failed"

echo -e "\nLogging in..."
LOGIN_RESP=$(curl -s -X POST http://localhost:4000/api/login \
  -H "Content-Type: application/json" \
  -d "{\"email_or_username\": \"$EMAIL\", \"password\": \"password123\"}")
echo $LOGIN_RESP | jq || echo "Login failed"

TOKEN=$(echo $LOGIN_RESP | grep -oE '"token":"[^"]+"' | cut -d'"' -f4)
if [ -z "$TOKEN" ]; then
  echo "Login failed, no token extracted!"
  exit 1
fi

echo -e "\nTesting API Gateway -> User & Auth Services (Me)"
curl -s -X GET http://localhost:4000/api/me -H "Authorization: Bearer $TOKEN" | jq

echo -e "\n\nTesting API Gateway -> Social Service (Follow)"
# Alice doesn't exist yet but backend might just insert follow record or reject it
curl -s -X POST http://localhost:4000/api/social/follow \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"target_email\": \"alice@ui.ac.id\"}" | jq

echo -e "\n\nTesting API Gateway -> Social Service (Profile)"
curl -s -X GET http://localhost:4000/api/social/profile/$EMAIL \
  -H "Authorization: Bearer $TOKEN" | jq

echo -e "\n\nAll integration tests finished."
