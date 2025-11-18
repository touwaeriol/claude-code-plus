package com.claudecodeplus.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import java.nio.file.Files

fun main(args: Array<String>) = runBlocking {
    println("🚀 Starting standalone Claude Code Plus server...")

    // 1. 创建模拟的 IDE 动作桥接器
    val mockIdeBridge = IdeActionBridge.Mock()
    println("🔧 Using Mock IdeActionBridge")

    // 2. 创建协程作用域
    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // 3. 创建一个临时的前端目录
    val tempFrontendDir = Files.createTempDirectory("claude-code-plus-frontend")
    val indexHtml = tempFrontendDir.resolve("index.html")
    Files.writeString(indexHtml, "<h1>Claude Code Plus Standalone Server</h1>")
    println("📂 Frontend directory created at: $tempFrontendDir")

    // 4. 实例化 HttpApiServer
    val server = HttpApiServer(
        ideActionBridge = mockIdeBridge,
        scope = scope,
        frontendDir = tempFrontendDir
    )

    // 5. 启动服务器并打印 URL
    try {
        val url = server.start()
        println("✅ Server started successfully at: $url")
        println("💡 Press Ctrl+C to stop the server.")

        // 保持主线程存活，直到服务器被外部停止
        while (true) {
            kotlinx.coroutines.delay(1000L)
        }
    } catch (e: Exception) {
        println("❌ Failed to start server: ${e.message}")
        e.printStackTrace()
    } finally {
        println("🛑 Stopping server...")
        server.stop()
        // 清理临时文件
        try {
            Files.walk(tempFrontendDir)
                .sorted(Comparator.reverseOrder())
                .forEach(Files::delete)
            println("🗑️  Cleaned up temporary frontend directory.")
        } catch (e: Exception) {
            println("⚠️ Failed to clean up temp directory: ${e.message}")
        }
    }
}

