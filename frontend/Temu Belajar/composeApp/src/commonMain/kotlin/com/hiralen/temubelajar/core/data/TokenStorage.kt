package com.hiralen.temubelajar.core.data

/**
 * TokenStorage — simple key-value storage untuk auth token.
 * Menggantikan Room database karena hanya butuh simpan/baca satu token string.
 *
 * Platform actuals:
 * - Android  → SharedPreferences
 * - Desktop  → Java Preferences API
 * - iOS      → NSUserDefaults
 * - wasmJs   → window.localStorage
 */
expect class TokenStorage() {
    fun saveToken(token: String)
    fun getToken(): String?
    fun clearToken()
    fun hasLoggedInBefore(): Boolean
}
