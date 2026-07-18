package com.hiralen.temubelajar.core.ui

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.content.Context

/**
 * Phase 5.36 — Android haptic implementation via Vibrator / VibratorManager
 * (the per-app VibratorManager premiered in API 31 / Android 12; legacy
 * Vibrator is still the only surface ≤ API 30). We pattern-match on the
 * device SDK because the constants `VibrationEffect.createPredefined` and
 * `EFFECT_TICK` were stable only from API 29 / API 30 onward.
 *
 * Patterns are short by design — phase 5.36 is "feels responsive" not
 * "vibrate the phone off the desk". Each call degrades to a no-op when
 * the device has no vibrator (`hasVibrator()` is false) or when the
 * platform-specific call throws (rare but seen on some cheap tablets).
 */

private fun vibrator(): Vibrator? {
    return try {
        val koin = org.koin.mp.KoinPlatform.getKoin()
        val ctx: Context = koin.get<Context>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val mgr = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            mgr?.defaultVibrator
        } else {
            @Suppress("DEPRECATION", "CallerMustBeMentionedInRequireAndroidApproval")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (_: Throwable) { null }
}

private fun vibrate(milliseconds: Long, amplitude: Int = VibrationEffect.DEFAULT_AMPLITUDE) {
    val v = vibrator() ?: return
    if (!v.hasVibrator()) return
    try {
        v.vibrate(VibrationEffect.createOneShot(milliseconds, amplitude))
    } catch (_: Throwable) {
        // Some OEM implementations throw on amplitude they can't meet —
        // fall back to amplitude-less buzz.
        try {
            @Suppress("DEPRECATION")
            v.vibrate(milliseconds)
        } catch (_: Throwable) { /* nothing else to try */ }
    }
}

actual fun platformHapticClick()    { vibrate(15) }
actual fun platformHapticSoft()     { vibrate(8,  60) }
actual fun platformHapticSuccess()  { vibrate(35, 90) }
actual fun platformHapticWarning()  { vibrate(60, 200) }
