package com.hiralen.temubelajar.core.data

import platform.Foundation.NSUserDefaults

actual class TokenStorage actual constructor() {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun saveToken(token: String) {
        defaults.setObject(token, forKey = "auth_token")
        defaults.setBool(true, forKey = "has_logged_in")
        defaults.synchronize()
    }

    actual fun getToken(): String? =
        defaults.stringForKey("auth_token")?.takeIf { it.isNotEmpty() }

    actual fun clearToken() {
        defaults.removeObjectForKey("auth_token")
        defaults.synchronize()
    }

    actual fun hasLoggedInBefore(): Boolean = defaults.boolForKey("has_logged_in")
}
