package com.hiralen.temubelajar.core.presentation

// ─── Backend URL Configuration ────────────────────────────────────────────────
// API Gateway (Phoenix/Elixir) listens on port 4000.
// All HTTP and WebSocket traffic is proxied through this single gateway.
//
// For development on emulator/simulator: use 10.0.2.2 (Android) or localhost (desktop/web)
// For development on physical device:    use your machine's LAN IP (e.g. 192.168.1.x)
// For production:                        replace with your real domain (e.g. https://api.temubelajar.id)
//
// HTTP endpoints routed by API Gateway:
//   POST   /api/register
//   POST   /api/verify-otp
//   POST   /api/resend-otp
//   POST   /api/login
//   POST   /api/logout
//   GET    /api/me
//   GET    /api/user/:email
//   PUT    /api/user/:email
//
// WebSocket topics routed by API Gateway:
//   matchmaking:lobby          — matchmaking channel
//   signaling:{pair_id}        — WebRTC signaling channel

const val BASE_URL = "http://192.168.1.4:4000"
// vsn=2.0.0 tells Phoenix to use V2.JSONSerializer (array frame format)
// which is what the frontend sends: [join_ref, ref, topic, event, payload]
const val BASE_WS_URL = "ws://192.168.1.4:4000/socket/websocket?vsn=2.0.0"
