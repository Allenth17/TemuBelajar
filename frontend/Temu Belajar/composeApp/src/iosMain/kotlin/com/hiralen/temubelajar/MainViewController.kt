package com.hiralen.temubelajar

import androidx.compose.ui.window.ComposeUIViewController
import com.hiralen.temubelajar.app.App
import com.hiralen.temubelajar.di.initKoin

fun MainViewController() = ComposeUIViewController(
    configure = {
        initKoin()
    }
) {
    App()
}