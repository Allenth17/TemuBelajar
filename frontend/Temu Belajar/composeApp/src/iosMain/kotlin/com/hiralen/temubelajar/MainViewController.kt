package com.hiralen.temubelajar

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController
fun MainViewController(): UIViewController {
    lateinit var controller: UIViewController
    val controller = ComposeUIViewController {
        App(context = it) // `it` = UIViewController instance
    }
    return controller
}