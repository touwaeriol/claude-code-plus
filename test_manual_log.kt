import com.claudecodeplus.util.ResponseLogger
import java.io.File

fun main() {
    println("测试日志记录功能")
    
    // 创建一个测试日志
    val logFile = ResponseLogger.createSessionLog("test-session", null)
    println("创建的日志文件: ${logFile.absolutePath}")
    println("文件存在: ${logFile.exists()}")
    println("文件大小: ${logFile.length()} bytes")
    
    // 记录一个请求
    ResponseLogger.logRequest(
        logFile,
        "TEST",
        "这是一个测试消息",
        mapOf("option1" to "value1", "option2" to "value2")
    )
    
    // 记录一个响应
    ResponseLogger.logResponseChunk(
        logFile,
        "text",
        "这是响应内容，包含一些 **Markdown** 格式和 \u001b[32m颜色\u001b[0m",
        null,
        mapOf("test" to true)
    )
    
    // 关闭日志
    ResponseLogger.closeSessionLog(logFile)
    
    println("\n日志内容:")
    println(logFile.readText())
}