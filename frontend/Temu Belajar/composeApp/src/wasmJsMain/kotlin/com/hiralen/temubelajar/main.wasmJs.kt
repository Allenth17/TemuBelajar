    package com.hiralen.temubelajar

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.hiralen.temubelajar.app.RootComponent
import com.hiralen.temubelajar.app.RootContent
import com.hiralen.temubelajar.core.di.initKoin
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // Init Koin DI (corePlatformModule via actual)
    initKoin()

    // Decompose lifecycle + root component
    val lifecycle = LifecycleRegistry()
    val rootComponent = RootComponent(
        componentContext = DefaultComponentContext(lifecycle = lifecycle)
    )

    // Render Compose app ke document.body
    ComposeViewport(document.body!!) {
        RootContent(component = rootComponent)
    }
}
