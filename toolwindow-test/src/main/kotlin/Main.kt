import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.ComponentStyling
import com.claudecodeplus.test.JewelChatTestApp

fun main() = application {
    val windowState = rememberWindowState(
        width = 1000.dp,
        height = 700.dp
    )
    
    Window(
        onCloseRequest = ::exitApplication,
        title = "Jewel Chat Test",
        state = windowState
    ) {
        IntUiTheme {
            JewelChatTestApp()
        }
    }
} 