package com.hiralen.temubelajar.core.ui

/**
 * Phase 5.36 — cross-platform haptic ticks for the keybutton press sites.
 *
 * Why an `expect/actual` surface instead of a single Compose Multiplatform
 * helper: Compose's own haptic API (`LocalHapticFeedback.performHapticFeedback`)
 * is wired to Android's `HapticFeedbackConstants` and to a JVM no-op for
 * Desktop, leaving iOS and WASM without a unified implementation. We need
 * behaviour closer to "Vibrate on Android + WASM (via navigator.vibrate),
 * UIDevice generator on iOS, no-op on Desktop JVM." So the engine is split
 * per platform actual — commonMain declares the four granularities we use.
 *
 * Each function is safe to call from any thread (the actuals dispatch to the
 * UI thread / browser main thread as needed) and degrades to a no-op when
 * the platform has no haptic hardware (Desktop JVM in particular).
 */

/**
 * Light tap — used on every primary action button click (login submit,
 * send chat, end call, "next person"). Single-frame haptic so the user
 * feels the click register without an aggressive buzz.
 */
expect fun platformHapticClick()

/**
 * Soft confirmation — used by toggle actions (mic off / camera off /
 * speakerphone). Gentler than a click so a rapid sequence of toggles
 * doesn't accumulate into a hard vibration.
 */
expect fun platformHapticSoft()

/**
 * Success tick — used on rare "you got a match" + report-submitted events,
 * a slightly longer / stronger pattern than a plain click so the user
 * recognises a meaningful state change.
 */
expect fun platformHapticSuccess()

/**
 * Warning tick — used for destructive-confirm buttons (Block, Report,
 * End call). Pitched differently so the user recognises the consequence
 * before the action commits.
 */
expect fun platformHapticWarning()
