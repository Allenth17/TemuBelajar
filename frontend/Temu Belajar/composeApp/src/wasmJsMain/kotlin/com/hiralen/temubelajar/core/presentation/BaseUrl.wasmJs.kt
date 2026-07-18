package com.hiralen.temubelajar.core.presentation

// wasmJs — no JVM system properties. Production overrides land via
// webpack `define` / `process.env` injection (see composeApp wasmJs
// commonWebpackConfig in build.gradle.kts). For now dev defaults apply.
actual fun systemProperty(name: String): String? = null
