import com.claudecodeplus.ui.services.SessionHistoryService
import com.claudecodeplus.ui.services.SessionLoader
import com.claudecodeplus.ui.services.MessageProcessor
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    println("=== 测试历史会话加载 ===")
    
    val sessionHistoryService = SessionHistoryService()
    val messageProcessor = MessageProcessor()
    val sessionLoader = SessionLoader(sessionHistoryService, messageProcessor)
    
    val projectPath = "C:\\Users\\16790\\IdeaProjects\\claude-code-plus\\desktop"
    val sessionFile = sessionHistoryService.getLatestSessionFile(projectPath)
    
    if (sessionFile != null) {
        println("加载会话文件: ${sessionFile.name}")
        
        var messageCount = 0
        sessionLoader.loadSessionAsMessageFlow(sessionFile, maxMessages = 5)
            .collect { result ->
                when (result) {
                    is SessionLoader.LoadResult.MessageCompleted -> {
                        messageCount++
                        val msg = result.message
                        println("\n消息 #$messageCount [${msg.role}]:")
                        println("  内容: ${msg.content.take(100)}...")
                        println("  模型: ${msg.model?.displayName ?: "无"}")
                        println("  工具调用: ${msg.toolCalls.size}")
                        println("  有序元素: ${msg.orderedElements.size}")
                    }
                    is SessionLoader.LoadResult.LoadComplete -> {
                        println("\n加载完成！共 ${result.messages.size} 条消息")
                    }
                    is SessionLoader.LoadResult.Error -> {
                        println("\n错误: ${result.error}")
                    }
                    else -> {}
                }
            }
    } else {
        println("未找到会话文件")
    }
}