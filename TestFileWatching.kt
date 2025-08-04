import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.util.UUID
import com.claudecodeplus.sdk.session.SessionFileWatchService
import com.claudecodeplus.sdk.session.UnifiedSessionAPI

fun main() = runBlocking {
    println("=== 文件监听实时性测试 ===")
    
    // 测试参数
    val projectPath = "C:\\Users\\16790\\IdeaProjects\\claude-code-plus\\desktop"
    val sessionId = UUID.randomUUID().toString()
    val sessionFilePath = "C:\\Users\\16790\\.claude\\projects\\C--Users-16790-IdeaProjects-claude-code-plus-desktop\\$sessionId.jsonl"
    
    println("项目路径: $projectPath")
    println("会话ID: $sessionId")
    println("会话文件: $sessionFilePath")
    
    // 创建测试服务
    val scope = CoroutineScope(Dispatchers.Default)
    val unifiedAPI = UnifiedSessionAPI(scope)
    
    // 启动项目监听
    unifiedAPI.startProject(projectPath)
    println("\n✅ 已启动项目监听")
    
    // 创建会话文件
    val sessionFile = File(sessionFilePath)
    sessionFile.parentFile.mkdirs()
    
    // 写入初始消息
    val initialMessage = """{"type":"user","message":{"role":"user","content":"初始测试消息"},"uuid":"${UUID.randomUUID()}","timestamp":"${System.currentTimeMillis()}"}"""
    sessionFile.writeText(initialMessage + "\n")
    println("\n✅ 已创建会话文件并写入初始消息")
    
    // 启动监听协程
    val listenJob = scope.launch {
        println("\n🎧 开始监听会话消息...")
        unifiedAPI.subscribeToSession(sessionId, projectPath)
            .collect { messages ->
                println("\n📨 收到消息更新！消息数: ${messages.size}")
                messages.forEach { msg ->
                    println("  - ${msg.type}: ${msg.message}")
                }
            }
    }
    
    // 等待监听启动
    delay(1000)
    
    // 模拟实时消息更新
    println("\n📝 模拟实时消息更新...")
    
    repeat(3) { i ->
        delay(2000) // 每2秒发送一条消息
        
        val newMessage = when(i) {
            0 -> """{"type":"assistant","message":{"role":"assistant","content":[{"type":"text","text":"这是助手的第一条响应"}]},"uuid":"${UUID.randomUUID()}","timestamp":"${System.currentTimeMillis()}"}"""
            1 -> """{"type":"user","message":{"role":"user","content":"这是用户的第二条消息"},"uuid":"${UUID.randomUUID()}","timestamp":"${System.currentTimeMillis()}"}"""
            2 -> """{"type":"assistant","message":{"role":"assistant","content":[{"type":"text","text":"这是助手的最终响应"}]},"uuid":"${UUID.randomUUID()}","timestamp":"${System.currentTimeMillis()}"}"""
            else -> ""
        }
        
        // 追加到文件
        sessionFile.appendText(newMessage + "\n")
        println("\n✍️ 已追加消息 ${i + 1}")
    }
    
    // 等待最后的消息被处理
    delay(3000)
    
    println("\n🏁 测试完成！")
    
    // 清理
    listenJob.cancel()
    sessionFile.delete()
    println("✅ 已清理测试文件")
    
    // 关闭服务
    unifiedAPI.shutdown()
    scope.cancel()
}