package com.hiralen.temubelajar

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import java.awt.Component

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Temu Belajar",
    ) {
        App(
            platformContext = PlatformContext(this.window)
        )
    }
}