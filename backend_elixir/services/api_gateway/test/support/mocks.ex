# Phase 8.10 — Test support for stubbing the gateway's downstream HTTP layer
# and Bearer→email resolution.
Mox.defmock(ApiGateway.HTTPClientMock, for: ApiGateway.HTTPClient)
Mox.defmock(ApiGateway.AuthVerifierMock, for: ApiGateway.AuthVerifier)
