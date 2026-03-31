#!/usr/bin/env bash
# ⚠️  DEPRECATED — Monolithic backend
#
# The monolith (backend_elixir/lib/) has been superseded by the microservices
# architecture under backend_elixir/services/.
#
# Use start_all.sh to launch all microservices instead:
#
#   ./start_all.sh
#
# The monolith is kept for reference ONLY and must NOT be run in parallel
# with the microservices (port conflict on 4000).

echo ""
echo "╔══════════════════════════════════════════════════════════╗"
echo "║  ⚠️  DEPRECATED: Monolithic backend                      ║"
echo "║                                                          ║"
echo "║  This backend has been replaced by microservices.        ║"
echo "║  Use ./start_all.sh to start all microservices instead.  ║"
echo "╚══════════════════════════════════════════════════════════╝"
echo ""
echo "If you intentionally want to start the monolith for debugging,"
echo "run: MIX_ENV=dev mix phx.server"
echo "(Ensure no microservice is already occupying port 4000)"
echo ""
exit 1
