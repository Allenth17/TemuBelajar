package com.hiralen.temubelajar.core.data

import java.util.prefs.Preferences

actual class TokenStorage actual constructor() {
    private val prefs = Preferences.userRoot().node("temubelajar")

    actual fun saveToken(token: String) { 
        prefs.put("auth_token", token) 
        prefs.putBoolean("has_logged_in", true)
    }
    actual fun getToken(): String? = prefs.get("auth_token", null)?.takeIf { it.isNotEmpty() }
    actual fun clearToken() { prefs.remove("auth_token") }
    actual fun hasLoggedInBefore(): Boolean = prefs.getBoolean("has_logged_in", false)
}
