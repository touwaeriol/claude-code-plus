import com.claudecodeplus.ui.services.SessionFileWatchService
import kotlinx.coroutines.*
import java.io.File

/**
 * 简单的文件监听测试
 */
suspend fun main() {
    val projectPath = "/Users/erio/codes/idea/claude-code-plus"
    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    println("=== 文件监听测试 ===")
    println("项目路径: $projectPath")
    
    val watchService = SessionFileWatchService(scope)
    
    // 启动监听
    println("启动文件监听...")
    watchService.startWatchingProject(projectPath)
    
    // 获取会话目录
    val sessionsDir = watchService.getSessionsDirectory(projectPath)
    println("会话目录: ${sessionsDir.absolutePath}")
    println("目录是否存在: ${sessionsDir.exists()}")
    
    if (sessionsDir.exists()) {
        val files = sessionsDir.listFiles { file -> file.name.endsWith(".jsonl") }
        println("现有会话文件:")
        files?.forEach { file ->
            println("  - ${file.name} (${file.length()} bytes, 修改时间: ${java.util.Date(file.lastModified())})")
        }
    }
    
    // 订阅所有消息更新
    val job = scope.launch {
        println("开始订阅文件变化...")
        watchService.subscribeToAll().collect { event ->
            println("🎯 检测到文件变化:")
            println("   会话ID: ${event.sessionId}")
            println("   项目路径: ${event.projectPath}")
            println("   新消息数量: ${event.messages.size}")
            event.messages.forEach { msg ->
                println("   消息: ${msg.role} - ${msg.content?.take(50)}...")
            }
        }
    }
    
    println("\n监听已启动，等待文件变化...")
    println("你现在可以通过桌面应用发送消息，看是否有文件变化事件...")
    
    // 测试创建一个新文件
    delay(2000)
    println("\n📝 创建测试文件...")
    val testFile = File(sessionsDir, "test-${System.currentTimeMillis()}.jsonl")
    testFile.writeText("""{"type":"user","content":"测试消息","timestamp":"${System.currentTimeMillis()}"}""")
    println("测试文件已创建: ${testFile.name}")
    
    // 等待一段时间观察
    delay(5000)
    
    // 修改测试文件
    println("\n📝 修改测试文件...")
    testFile.appendText("""\n{"type":"assistant","content":"测试回复","timestamp":"${System.currentTimeMillis()}"}""")
    println("测试文件已修改")
    
    delay(3000)
    
    // 清理
    println("\n🧹 清理测试文件...")
    testFile.delete()
    
    delay(2000)
    
    job.cancel()
    watchService.stopAll()
    println("测试完成")
}