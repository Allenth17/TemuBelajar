# TemuBelajar Android app proguard rules
#
# Phase 8.6 / 8.7 — release (isMinifyEnabled = true) keep rules.
# Anything called only via reflection, JNI, or kotlinx-serialization MUST
# be kept explicitly — R8 can't see those call sites at build time.

# ── kotlinx.serialization DTOs ────────────────────────────────────────────────
# The compiler plugin generates serializers as static fields on the
# annotated class; stripping or renaming them breaks Json.parse at runtime.
-keepattributes *Annotation*, InnerClasses, Signature, Deprecated
-keep,includedescriptorclasses class com.hiralen.temubelajar.**$$serializer { *; }
-keepclassmembers,includedescriptorclasses class com.hiralen.temubelajar.** {
    *** Companion;
}
-keepclasseswithmembers,includedescriptorclasses class com.hiralen.temubelajar.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep @kotlinx.serialization.Serializable class com.hiralen.temubelajar.** { *; }

# ── Ktor (OkHttp engine) — HTTP/2, WebSocket, deframe callsites ───────────────
-keep class io.ktor.** { *; }
-keep class io.ktor.client.** { *; }
-keep class io.ktor.client.plugins.websocket.** { *; }
-keep class io.ktor.serialization.kotlinx.json.** { *; }
-dontwarn io.ktor.**
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# OkHttp + Okio — uses reflection to find platform TLS providers
-keep class okhttp3.** { *; }
-keep interface okhttp3.internal.** { *; }
-keep class okio.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# ── Coroutines — keep internal dispatcher lookup ─────────────────────────────
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keep class kotlinx.coroutines.android.AndroidExceptionPreHandler { *; }

# ── WebRTC (Google libwebrtc JNI) ────────────────────────────────────────────
# stream-webrtc-android surfaces a Java API layer over a native (.so) engine;
# R8 must NOT strip the dispatcher entry points called from native code.
-keep class org.webrtc.** { *; }
-keep class io.getstream.webrtc.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# ── Compose runtime — @Composable lambdas invoked reflectively ───────────────
-keep class androidx.compose.** { *; }
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-dontwarn androidx.compose.**

# ── Decompose — Essenty lifecycle reflection ─────────────────────────────────
-keep class com.arkivanov.decompose.** { *; }
-keep class com.arkivanov.essenty.** { *; }

# ── Koin — runtime bytecode introspection ────────────────────────────────────
-keep class org.koin.** { *; }
-keep class * extends org.koin.core.module.Module { *; }
-keep @org.koin.core.annotation.Module class * { *; }
-keep @org.koin.core.annotation.Single class * { *; }
-keep @org.koin.core.annotation.Factory class * { *; }

# ── Coil (image loader) — OkHttp + ServiceLoader ─────────────────────────────
-keep class coil3.** { *; }
-dontwarn coil3.**

# ── Compose Multiplatform / Compose resources ────────────────────────────────
# resource accessors lookup by generated Names
-keep class com.hiralen.temubelajar.**.resources.** { *; }
-keep class **.compose_resources.** { *; }

# ── BuildConfig (so release builds retain BASE_URL/BASE_WS_URL accesses) ─────
-keep class com.hiralen.temubelajar.BuildConfig { *; }
