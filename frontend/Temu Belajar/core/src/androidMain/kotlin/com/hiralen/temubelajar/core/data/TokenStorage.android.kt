package com.hiralen.temubelajar.core.data

import android.content.Context

actual class TokenStorage actual constructor() {
    private val prefs = AppContext.get()
        .getSharedPreferences("temubelajar_prefs", Context.MODE_PRIVATE)

    actual fun saveToken(token: String) {
        prefs.edit()
            .putString("auth_token", token)
            .putBoolean("has_logged_in", true)
            .apply()
    }

    actual fun getToken(): String? = prefs.getString("auth_token", null)

    actual fun clearToken() {
        prefs.edit().remove("auth_token").apply()
    }

    actual fun hasLoggedInBefore(): Boolean = prefs.getBoolean("has_logged_in", false)
}

/** Semaphore untuk mendapatkan Application Context tanpa DI */
object AppContext {
    private lateinit var ctx: Context
    fun init(context: Context) { ctx = context.applicationContext }
    fun get(): Context = ctx
}
