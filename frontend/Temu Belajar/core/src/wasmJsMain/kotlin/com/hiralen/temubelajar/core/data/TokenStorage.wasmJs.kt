package com.hiralen.temubelajar.core.data

import kotlinx.browser.localStorage

actual class TokenStorage actual constructor() {
    actual fun saveToken(token: String) { 
        localStorage.setItem("auth_token", token) 
        localStorage.setItem("has_logged_in", "true")
    }
    actual fun getToken(): String? = localStorage.getItem("auth_token")?.takeIf { it.isNotEmpty() }
    actual fun clearToken() { localStorage.removeItem("auth_token") }
    actual fun hasLoggedInBefore(): Boolean = localStorage.getItem("has_logged_in") == "true"
}
