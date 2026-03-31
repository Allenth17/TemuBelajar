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
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val rootComponent = RootComponent(componentContext = defaultComponentContext())
        setContent {
            RootContent(component = rootComponent)
        }
    }
}