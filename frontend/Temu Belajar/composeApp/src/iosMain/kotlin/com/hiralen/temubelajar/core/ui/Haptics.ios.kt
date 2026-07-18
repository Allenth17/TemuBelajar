package com.hiralen.temubelajar.core.ui

import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle

/**
 * Phase 5.36 — iOS haptic implementation via the UIKit impact generator.
 * Each mapping uses the closest predefined style:
 *   Click    → Light impact — primary-button press "tick"
 *   Soft     → Soft impact  — Toggle actions that happen in quick sequence
 *   Success  → Medium impact — Match-found / report-submitted "thump"
 *   Warning  → Heavy impact — Destructive-confirm action
 * `UIImpactFeedbackGenerator` is intended to be prepared +Autoreleased each
 * call site. We let the system pool since this surface is invoked < 10×/min
 * at worst, no per-call autoreleasepool is required.
 */
actual fun platformHapticClick()    { UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleLight).impactOccurred() }
actual fun platformHapticSoft()     { UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleSoft).impactOccurred() }
actual fun platformHapticSuccess()  { UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium).impactOccurred() }
actual fun platformHapticWarning()  { UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy).impactOccurred() }
