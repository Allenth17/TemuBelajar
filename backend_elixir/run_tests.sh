#!/usr/bin/env bash

# run_tests.sh
# Shell script to run all unit, api, genserver, and websocket tests for all microservices.

echo "================================================="
echo "   TemuBelajar Elixir Backend Test Runner"
echo "   Microservices Architecture"
echo "================================================="

cd /home/allenth/AndroidStudioProjects/TemuBelajar/backend_elixir

# Track overall success
OVERALL_SUCCESS=0

# Function to run tests for a service
run_service_tests() {
    local service_name=$1
    local service_path=$2
    
    echo ""
    echo "---------------------------------------------------"
    echo "Testing $service_name"
    echo "---------------------------------------------------"
    
    if [ ! -d "$service_path" ]; then
        echo "⚠️  WARNING: $service_name directory not found, skipping..."
        return 0
    fi
    
    cd "$service_path"
    
    # Ensure dependencies are available and compiled for test
    echo "=> Setting up test environment for $service_name..."
    export MIX_ENV=test
    mix deps.get
    
    echo "=> Running test suite for $service_name..."
    mix test --cover
    
    local test_result=$?
    
    if [ $test_result -eq 0 ]; then
        echo "✅ SUCCESS: $service_name tests passed!"
    else
        echo "❌ ERROR: $service_name tests failed!"
        OVERALL_SUCCESS=1
    fi
    
    cd /home/allenth/AndroidStudioProjects/TemuBelajar/backend_elixir
}

# Test all microservices
run_service_tests "Auth Service" "services/auth_service"
run_service_tests "User Service" "services/user_service"
run_service_tests "Email Service" "services/email_service"
run_service_tests "Signaling Service" "services/signaling_service"
run_service_tests "Matchmaking Service" "services/matchmaking_service"
run_service_tests "API Gateway" "services/api_gateway"

# Also test the monolith (for backward compatibility)
echo ""
echo "---------------------------------------------------"
echo "Testing Monolith (for backward compatibility)"
echo "---------------------------------------------------"
export MIX_ENV=test
mix deps.get
mix test --cover

if [ $? -eq 0 ]; then
    echo "✅ SUCCESS: Monolith tests passed!"
else
    echo "❌ ERROR: Monolith tests failed!"
    OVERALL_SUCCESS=1
fi

echo ""
echo "================================================="
if [ $OVERALL_SUCCESS -eq 0 ]; then
    echo "✅ SUCCESS: All tests executed successfully!"
    echo "================================================="
    exit 0
else
    echo "❌ ERROR: One or more tests failed. Check logs."
    echo "================================================="
    exit 1
fi
