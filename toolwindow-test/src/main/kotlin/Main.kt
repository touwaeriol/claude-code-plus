import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.ComponentStyling
import com.claudecodeplus.ui.jewel.JewelChatApp
import com.claudecodeplus.sdk.ClaudeCliWrapper
import com.claudecodeplus.ui.models.AiModel
import com.claudecodeplus.test.ContextSelectorTestApp

@Composable
@Preview
fun ModelSwitchTest() {
    var selectedModel by remember { mutableStateOf(AiModel.SONNET) }
    
    println("ModelSwitchTest: selectedModel = ${selectedModel.displayName}")
    
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("模型切换测试", style = MaterialTheme.typography.h4)
        
        Text("当前模型: ${selectedModel.displayName}")
        
        Button(onClick = {
            println("Button clicked - before: ${selectedModel.displayName}")
            selectedModel = when (selectedModel) {
                AiModel.SONNET -> AiModel.OPUS
                AiModel.OPUS -> AiModel.SONNET
            }
            println("Button clicked - after: ${selectedModel.displayName}")
        }) {
            Text("切换模型")
        }
        
        // 测试回调机制
        TestCallback(
            currentModel = selectedModel,
            onModelChange = { model ->
                println("TestCallback: onModelChange called with ${model.displayName}")
                selectedModel = model
            }
        )
    }
}

@Composable
fun TestCallback(
    currentModel: AiModel,
    onModelChange: (AiModel) -> Unit
) {
    println("TestCallback: currentModel = ${currentModel.displayName}")
    
    Button(onClick = {
        println("TestCallback: Button clicked")
        val nextModel = when (currentModel) {
            AiModel.SONNET -> AiModel.OPUS
            AiModel.OPUS -> AiModel.SONNET
        }
        println("TestCallback: Calling onModelChange with ${nextModel.displayName}")
        onModelChange(nextModel)
    }) {
        Text("回调测试: ${currentModel.displayName}")
    }
}

fun main() = application {
    val windowState = rememberWindowState(
        width = 800.dp,
        height = 600.dp
    )
    
    Window(
        onCloseRequest = ::exitApplication,
        title = "上下文选择器测试",
        state = windowState
    ) {
        IntUiTheme {
            ContextSelectorTestApp()
        }
    }
} 