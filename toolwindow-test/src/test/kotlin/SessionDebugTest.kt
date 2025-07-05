import com.claudecodeplus.session.ClaudeSessionManager
import kotlinx.coroutines.runBlocking
import java.io.File

fun main() {
    println("=== Testing Claude Session Path ===")
    
    val projectPath = "/Users/erio/codes/idea/claude-code-plus"
    val encodedPath = "-" + projectPath.replace("/", "-")
    val homeDir = System.getProperty("user.home")
    val sessionDir = File(homeDir, ".claude/projects/$encodedPath")
    
    println("Project Path: $projectPath")
    println("Encoded Path: $encodedPath")
    println("Session Directory: ${sessionDir.absolutePath}")
    println("Directory exists: ${sessionDir.exists()}")
    
    if (sessionDir.exists()) {
        val files = sessionDir.listFiles()?.filter { it.extension == "jsonl" } ?: emptyList()
        println("\nFound ${files.size} JSONL files")
        
        // 显示前5个文件
        files.take(5).forEach { file ->
            println("- ${file.name} (${file.length()} bytes)")
        }
    }
    
    // 测试 SessionManager
    println("\n=== Testing SessionManager ===")
    val sessionManager = ClaudeSessionManager()
    
    runBlocking {
        try {
            val sessions = sessionManager.getSessionList(projectPath)
            println("SessionManager returned ${sessions.size} sessions")
            
            if (sessions.isNotEmpty()) {
                println("\nFirst 3 sessions:")
                sessions.take(3).forEach { session ->
                    println("- Session: ${session.sessionId}")
                    println("  Messages: ${session.messageCount}")
                    println("  Last message: ${session.lastMessage?.take(50)}...")
                }
            }
        } catch (e: Exception) {
            println("Error: ${e.message}")
            e.printStackTrace()
        }
    }
}