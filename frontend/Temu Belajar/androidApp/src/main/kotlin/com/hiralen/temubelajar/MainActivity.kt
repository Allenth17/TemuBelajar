package com.hiralen.temubelajar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.arkivanov.decompose.defaultComponentContext
import com.hiralen.temubelajar.app.RootComponent
import com.hiralen.temubelajar.app.RootContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Phase 8.19 — AOSP examples (and Android Components docs) call
        // enableEdgeToEdge() AFTER super.onCreate. Calling it before lets the
        // window insets machinery run before the activity is fully attached,
        // which can produce a wrong first-frame system bars configuration.
        enableEdgeToEdge()
        val rootComponent = RootComponent(componentContext = defaultComponentContext())
        setContent {
            RootContent(component = rootComponent)
        }
    }
}