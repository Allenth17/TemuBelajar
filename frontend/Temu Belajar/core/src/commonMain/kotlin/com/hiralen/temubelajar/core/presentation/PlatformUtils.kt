package com.hiralen.temubelajar.core.presentation

/**
 * PlatformUtils — platform-specific layout helpers.
 *
 * isScrollableLayout:
 *   true  → Android / iOS (mobile — content scrolls when it overflows the viewport)
 *   false → Desktop JVM / wasmJs (wide fixed layout — content is sized to fit, no scroll)
 *
 * Usage in Composables:
 *   val scrollMod = if (isScrollableLayout) Modifier.verticalScroll(rememberScrollState()) else Modifier
 */
expect val isScrollableLayout: Boolean
