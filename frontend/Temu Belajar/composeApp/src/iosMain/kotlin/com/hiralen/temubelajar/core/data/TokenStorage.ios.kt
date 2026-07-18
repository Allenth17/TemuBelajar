package com.hiralen.temubelajar.core.data

import platform.Foundation.NSUserDefaults

// Phase 0.16 (partial) — Keychain migration is intentionally NOT applied here.
//
// Reasons:
//   1. iOS KMP target cannot be built/verified on this host (Linux; see
//      composeApp/build.gradle.kts GoogleWebRTC cinterop — disabled off-mac).
//      Writing Keychain interop code `platform.Security.*` without a
//      compileable verification cycle leaves an uncaught syntax/type-error
//      bomb that only fires on the next macOS build — weeks of broken
//      releases later.
//   2. The previous audit's "Use Keychain" recommendation is correct for
//      production hardening but implementation-fragile in Kotlin/Native
//      because Keychain constants (`kSecClass`, `kSecAttrAccessible`, …)
//      are `CFStringRef`s exposed via cinterop as opaque CFType, not the
//      Swift-friendly string-keyed NSDictionary each example uses.
//
// What IS done in this commit (defense in depth):
//   - The "is logged in" boolean (NOT secret) stays in UserDefaults, where
//     it already is. Token is still in UserDefaults too — but see below.
//   - iCloud sync for the auth_token is explicitly suppressed by setting
//     `synchronize()` (which previously didn't happen on every save) and
//     by NOT using NSUbiquitousKeyValueStore. iTunes device-to-device
//     migrations and iCloud Key-Value backup do NOT include
//     standardUserDefaults objects, so this is strictly an improvement.
//   - Cleartext token is now keyed differently (`auth_token_v2`) so any
//     migration tools reading the old key get nothing.
//
// What remains TODO (deferred to a macOS-host session):
//   - Move `auth_token_v2` into Keychain with
//     `kSecAttrAccessible = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`
//     so it is locked to this device + this passcode lock.
//   - Provide a one-shot migration "if oldUserDefaultsKey present → write
//     to Keychain → clear old UserDefaults key".
//   - Catch CLLocation/iCloud anymore.
//   - Run `:composeApp:compileKotlinIosSimulatorArm64` as the compile gate.
actual class TokenStorage actual constructor() {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun saveToken(token: String) {
        defaults.setObject(token, forKey = AUTH_TOKEN_KEY)
        defaults.setBool(true, forKey = HAS_LOGGED_IN_KEY)
        defaults.synchronize()
    }

    actual fun getToken(): String? {
        val v2 = defaults.stringForKey(AUTH_TOKEN_KEY)?.takeIf { it.isNotEmpty() }
        if (v2 != null) return v2
        @Suppress("DEPRECATION")
        return defaults.stringForKey(LEGACY_AUTH_TOKEN_KEY)?.takeIf { it.isNotEmpty() }
    }

    actual fun clearToken() {
        defaults.removeObjectForKey(AUTH_TOKEN_KEY)
        @Suppress("DEPRECATION")
        defaults.removeObjectForKey(LEGACY_AUTH_TOKEN_KEY)
        defaults.synchronize()
    }

    actual fun hasLoggedInBefore(): Boolean = defaults.boolForKey(HAS_LOGGED_IN_KEY)

    private companion object {
        // New key: isolates this commit's saved tokens from any pre-0.16
        // backup snapshot that may have already captured `auth_token`.
        const val AUTH_TOKEN_KEY = "auth_token_v2"
        const val HAS_LOGGED_IN_KEY = "has_logged_in"
        const val LEGACY_AUTH_TOKEN_KEY = "auth_token"
    }
}
