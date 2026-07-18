package com.hiralen.temubelajar.core.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

// Phase 0.15 — token is stored in EncryptedSharedPreferences so backup
// extraction / `adb shell run-as cat` cannot read it in cleartext.
// Master keys live in the Android Keystore (hardware-backed on devices
// that support it). If device provisioning fails we fall back to a
// private-mode plaintext file (still better than the old shared prefs
// because `android:allowBackup="false"` is set on the application).
actual class TokenStorage actual constructor() {
    private val context: Context = AppContext.get()

    private val prefs: SharedPreferences by lazy { createPrefs() }

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

    /**
     * Build an EncryptedSharedPreferences instance. We attempt the
     * Keystore-backed MasterKey first; any failure degrades to a
     * MODE_PRIVATE file so the app stays usable on prototype devices.
     */
    private fun createPrefs(): SharedPreferences {
        return runCatching {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                "temubelajar_secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }.getOrElse {
            context.getSharedPreferences("temubelajar_prefs_fallback", Context.MODE_PRIVATE)
        }
    }
}

/**
 * Application context holder — initial injection happens in TeBeApp.onCreate.
 */
object AppContext {
    private lateinit var ctx: Context
    fun init(context: Context) { ctx = context.applicationContext }
    fun get(): Context = ctx
}
