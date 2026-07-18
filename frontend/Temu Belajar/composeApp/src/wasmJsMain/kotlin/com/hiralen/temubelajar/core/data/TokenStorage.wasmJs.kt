package com.hiralen.temubelajar.core.data

import kotlinx.browser.sessionStorage
import kotlinx.browser.localStorage

// Phase 0.16 — token moved from `localStorage` (persistent, XSS-readable,
// expires never) to `sessionStorage` (per-tab, cleared when the tab closes).
// XSS scripts in the same origin can still read the token during a single
// session — that's a separate Phase-0-class risk that requires CSP + trusted
// types, not a storage swap — but no longer leaks across sessions.
//
// The "has logged in before" boolean is the only thing intentionally kept
// in `localStorage`, because it is not secret and should survive tab close
// so the splash screen routes to login rather than onboarding.
actual class TokenStorage actual constructor() {
    actual fun saveToken(token: String) {
        sessionStorage.setItem(AUTH_TOKEN_KEY, token)
        localStorage.setItem(HAS_LOGGED_IN_KEY, "true")
    }
    actual fun getToken(): String? =
        sessionStorage.getItem(AUTH_TOKEN_KEY)?.takeIf { it.isNotEmpty() }
    actual fun clearToken() {
        sessionStorage.removeItem(AUTH_TOKEN_KEY)
    }
    actual fun hasLoggedInBefore(): Boolean = localStorage.getItem(HAS_LOGGED_IN_KEY) == "true"
}

private const val AUTH_TOKEN_KEY = "auth_token"
private const val HAS_LOGGED_IN_KEY = "has_logged_in"
