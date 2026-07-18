package com.hiralen.temubelajar.core.presentation

// JVM (desktop) — read from -Dapi.url=… JVM properties injected by Gradle
// via the `application { applicationDefaultJvmArgs = … }` block in
// desktopApp/build.gradle.kts. Falls through to null when the property is
// unset, which makes BaseUrl.kt fall back to the dev defaults.
actual fun systemProperty(name: String): String? = System.getProperty(name)
