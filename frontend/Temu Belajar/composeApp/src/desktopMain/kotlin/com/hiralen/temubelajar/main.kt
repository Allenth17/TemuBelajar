package com.hiralen.temubelajar

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.hiralen.temubelajar.app.App
import com.hiralen.temubelajar.di.initKoin

fun main() = application {
    initKoin()
    Window(
        onCloseRequest = ::exitApplication,
        title = "Temu Belajar",
        undecorated = false,
        resizable = false,
        state = rememberWindowState(
            size = DpSize(
                width = 500.dp,
                height = 1000.dp
            ),
            position = WindowPosition.Aligned(
                alignment = Alignment.Center
            )
        )
    ) {
        App()
    }
}