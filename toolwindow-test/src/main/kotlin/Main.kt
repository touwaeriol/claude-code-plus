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
import com.claudecodeplus.test.SimpleFileIndexService
import com.claudecodeplus.ui.services.ProjectService

// 简单的ProjectService实现用于测试
class TestProjectService(private val projectPath: String) : ProjectService {
    override fun getProjectPath(): String = projectPath
    override fun getProjectName(): String = projectPath.substringAfterLast('/')
    override fun openFile(filePath: String, lineNumber: Int?) {
        println("TestProjectService: 打开文件 $filePath:$lineNumber")
    }
    override fun showSettings(settingsId: String?) {
        println("TestProjectService: 显示设置 $settingsId")
    }
}

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
    
    // 创建测试服务
    val workingDirectory = System.getProperty("user.dir")
    val fileIndexService = remember { SimpleFileIndexService() }
    val projectService = remember { TestProjectService(workingDirectory) }
    
    // 初始化文件索引服务
    LaunchedEffect(Unit) {
        fileIndexService.initialize(workingDirectory)
    }
    
    Window(
        onCloseRequest = ::exitApplication,
        title = "Claude Code Plus 聊天测试",
        state = windowState
    ) {
        IntUiTheme {
            JewelChatApp(
                cliWrapper = ClaudeCliWrapper(),
                workingDirectory = workingDirectory,
                fileIndexService = fileIndexService,
                projectService = projectService
            )
        }
    }
} 