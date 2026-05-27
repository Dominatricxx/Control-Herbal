import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.window.*
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import androidx.compose.ui.res.painterResource

fun main() = application {
    val windowState = rememberWindowState(placement = WindowPlacement.Maximized)
    
    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "Control Herbal",
        icon = painterResource("logo_desktop.png")
    ) {
        MaterialTheme {
            App()
        }
    }
}

@Composable
fun App() {
    var screen by remember { mutableStateOf("opening") }

    if (screen == "opening") {
        OpeningScreen(onNavigate = { screen = "dashboard" })
    } else {
        DesktopDashboard()
    }
}
