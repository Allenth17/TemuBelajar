package com.hiralen.temubelajar

import androidx.compose.ui.window.ComposeUIViewController
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.hiralen.temubelajar.app.RootComponent
import com.hiralen.temubelajar.app.RootContent
import com.hiralen.temubelajar.core.di.initKoin
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    initKoin()
    val lifecycle = LifecycleRegistry()
    val rootComponent = RootComponent(
        componentContext = DefaultComponentContext(lifecycle = lifecycle)
    )
    return ComposeUIViewController {
        RootContent(component = rootComponent)
    }
}