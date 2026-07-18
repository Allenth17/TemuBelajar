package com.hiralen.temubelajar.core.presentation

// iOS — production via Info.plist keys injected by the Xcode build. For
// now we mirror the dev defaults; a later change can read from
// NSBundle.mainBundle.object(forInfoDictionaryKey:) once the iOS shell is
// wired up.
actual fun systemProperty(name: String): String? = null
