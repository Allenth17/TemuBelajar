package com.hiralen.temubelajar

import android.app.Application
import android.content.ComponentCallbacks2
import com.hiralen.temubelajar.core.data.AppContext
import com.hiralen.temubelajar.core.di.initKoin
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger

class TeBeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContext.init(this)
        // core.di.initKoin() dengan extension setelah startKoin
        // Gunakan koin context builder secara manual untuk android
        org.koin.core.context.startKoin {
            androidLogger()
            androidContext(this@TeBeApp)
            // Module dari core diload via corePlatformModule + coreModule
            modules(
                com.hiralen.temubelajar.core.di.coreModule,
                com.hiralen.temubelajar.core.di.corePlatformModule
            )
        }
    }

    /**
     * Phase 1.20 — Android memory pressure.
     *
     * We map the OS trim-level to one of two actionable buckets and broadcast
     * via the cross-platform `MemoryPressure` channel defined in
     * `composeApp/src/commonMain/.../core/ui/MemoryPressure.kt`:
     *
     *   - TRIM_MEMORY_RUNNING_LOW / CRITICAL → CRITICAL — used by the active
     *     WebRtcManager to release the local camera if a call is NOT in
     *     progress (the engine running at Home, idle). A live call keeps
     *     resources until the user hangs up.
     *   - TRIM_MEMORY_BACKGROUND / UI_HIDDEN / MODERATE → BACKGROUND — used
     *     by Ktor + Compose caches; cleared by the runtime itself under JVM
     *     GC pressure, so we log only for now.
     *
     * Both paths still log so we can inform next-agent profiling sessions
     * where we actually lose memory.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                android.util.Log.i("TeBeApp", "Memory pressure: CRITICAL ($level) — emitting MemoryPressure.Critical")
                com.hiralen.temubelajar.core.ui.MemoryPressure.emitCritical()
            }
            else -> {
                android.util.Log.v("TeBeApp", "Memory pressure: BACKGROUND ($level)")
                com.hiralen.temubelajar.core.ui.MemoryPressure.emitBackground()
            }
        }
    }

    @Deprecated("Replaced by onTrimMemory()", replaceWith = ReplaceWith("onTrimMemory(level)"))
    override fun onLowMemory() {
        super.onLowMemory()
        // Forward the legacy single-shot callback to the granular dispatcher
        // so subscribers that already listen on `MemoryPressure.Critical` fire
        // here too.
        com.hiralen.temubelajar.core.ui.MemoryPressure.emitCritical()
    }
}
