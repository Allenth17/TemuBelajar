package com.hiralen.temubelajar

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.runtime.remember
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.hiralen.temubelajar.app.RootComponent
import com.hiralen.temubelajar.app.RootContent
import com.hiralen.temubelajar.core.di.initKoin

fun main() {
    initKoin()

    application {
        val lifecycle = remember { LifecycleRegistry() }
        val rootComponent = remember {
            RootComponent(
                componentContext = DefaultComponentContext(lifecycle = lifecycle)
            )
        }

        Window(
            onCloseRequest = ::exitApplication,
            title = "TemuBelajar",
            alwaysOnTop = false,
            undecorated = true,
            resizable = false,
            state = rememberWindowState(
                placement = androidx.compose.ui.window.WindowPlacement.Fullscreen
            )
        ) {
            RootContent(component = rootComponent)
        }
    }
}