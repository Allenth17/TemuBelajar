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
import com.arkivanov.essenty.lifecycle.destroy
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

        val windowState = rememberWindowState(
            position = WindowPosition(Alignment.Center),
            size = DpSize(1280.dp, 800.dp)
        )

        Window(
            // Phase 1.18 — push Lifecycle.Destroy into the Decompose tree before
            // the app exits so component `doOnDestroy` callbacks fire and the
            // WebRTC engine / sockets / AudioManager state get released instead
            // of relying on JVM teardown (which races the renderer).
            onCloseRequest = {
                lifecycle.destroy()
                exitApplication()
            },
            title = "TemuBelajar",
            state = windowState
        ) {
            androidx.compose.runtime.LaunchedEffect(Unit) {
                window.minimumSize = java.awt.Dimension(800, 600)
            }
            RootContent(component = rootComponent)
        }
    }
}
