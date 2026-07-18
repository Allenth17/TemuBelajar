package com.hiralen.temubelajar.core.presentation

// Android — the androidApp module exposes BASE_URL/BASE_WS_URL via
// BuildConfig injected from Gradle properties (see androidApp build.gradle.kts
// `buildConfigField` lines). composeApp reads them at runtime via
// Class.forName to avoid an upward module dependency on :androidApp.
//
// No JVM system property exists on Android, so we look up the BuildConfig
// value reflectively from the app's application id.
actual fun systemProperty(name: String): String? = try {
    val appId = "com.hiralen.temubelajar"
    val clazz = Class.forName("$appId.BuildConfig")
    val fieldName = when (name) {
        "api.url" -> "BASE_URL"
        "api.wsUrl" -> "BASE_WS_URL"
        else -> return null
    }
    val field = clazz.getDeclaredField(fieldName).apply { isAccessible = true }
    field.get(null) as? String
} catch (_: ClassNotFoundException) {
    null
} catch (_: NoSuchFieldException) {
    null
}
