package com.hiralen.temubelajar

import androidx.compose.ui.window.ComposeUIViewController
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.hiralen.temubelajar.app.RootComponent
import com.hiralen.temubelajar.app.RootContent
import com.hiralen.temubelajar.core.di.initKoin
import platform.UIKit.UIViewController

// Phase 1.19 — `MainViewController()` may be called more than once across
// the SwiftUI lifecycle. SwiftUI recreate semantics can call us from a
// preview, after a state-restore, etc. `initKoin()` uses `startKoin { }`
// which throws `KoinApplicationAlreadyStartedException` on the second
// invocation. We use an atomic flag to guarantee the Koin application graph
// is built at most once per-process.
private var koinInitialized = false

@Synchronized
private fun initKoinOnce() {
    if (koinInitialized) return
    initKoin()
    koinInitialized = true
}

fun MainViewController(): UIViewController {
    initKoinOnce()
    val lifecycle = LifecycleRegistry()
    val rootComponent = RootComponent(
        componentContext = DefaultComponentContext(lifecycle = lifecycle)
    )
    return ComposeUIViewController {
        RootContent(component = rootComponent)
    }
}
