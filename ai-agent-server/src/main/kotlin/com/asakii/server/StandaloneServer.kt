package com.asakii.server

import com.asakii.server.logging.StandaloneLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.logging.LogManager

fun main(args: Array<String>) = runBlocking {
    // 设置系统编码为 UTF-8，解决 Windows 控制台乱码问题
    System.setProperty("file.encoding", "UTF-8")
    System.setProperty("console.encoding", "UTF-8")
    
    // 配置 java.util.logging 使用 UTF-8 编码
    try {
        val loggingConfigStream: InputStream? = 
            Thread.currentThread().contextClassLoader.getResourceAsStream("logging.properties")
        if (loggingConfigStream != null) {
            LogManager.getLogManager().readConfiguration(loggingConfigStream)
            loggingConfigStream.close()
        }
    } catch (e: Exception) {
        // 如果配置失败，继续执行（某些环境可能不支持）
        System.err.println("⚠️ 无法加载 logging.properties: ${e.message}")
    }
    
    // 设置标准输出流编码为 UTF-8
    try {
        System.setOut(java.io.PrintStream(System.out, true, StandardCharsets.UTF_8))
        System.setErr(java.io.PrintStream(System.err, true, StandardCharsets.UTF_8))
    } catch (e: Exception) {
        // 如果设置失败，继续执行（某些环境可能不支持）
    }
    
    // Windows 控制台编码设置（如果可能）
    try {
        if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
            // 尝试设置 Windows 控制台代码页为 UTF-8
            // 使用 cmd /c 确保命令在当前控制台会话中执行
            val process = Runtime.getRuntime().exec("cmd /c chcp 65001 >nul")
            process.waitFor() // 等待命令完成
            // 注意：chcp 只影响当前控制台窗口，对于 IDE 运行可能无效
            // 但设置 System.out/System.err 的编码更重要
        }
    } catch (e: Exception) {
        // 如果设置失败，继续执行（某些环境可能不支持）
        // 在 IDE 中运行时，chcp 可能无效，但 PrintStream 编码设置仍然有效
    }
    
    println("🚀 Starting standalone Claude Code Plus server...")

    var projectRootOverride: File? = null
    var preferredPortOverride: Int? = null
    val positionalArgs = mutableListOf<String>()

    var i = 0
    while (i < args.size) {
        val arg = args[i]
        when {
            arg == "--cwd" -> {
                val value = args.getOrNull(i + 1)
                    ?: error("Missing value for --cwd")
                projectRootOverride = File(value)
                i += 2
                continue
            }
            arg.startsWith("--cwd=") -> {
                val value = arg.substringAfter("=")
                require(value.isNotBlank()) { "Missing value for --cwd" }
                projectRootOverride = File(value)
                i += 1
                continue
            }
            arg == "--port" -> {
                val value = args.getOrNull(i + 1)
                    ?: error("Missing value for --port")
                preferredPortOverride = value.toIntOrNull()
                    ?: error("Invalid port: $value")
                i += 2
                continue
            }
            arg.startsWith("--port=") -> {
                val value = arg.substringAfter("=")
                preferredPortOverride = value.toIntOrNull()
                    ?: error("Invalid port: $value")
                i += 1
                continue
            }
            arg.startsWith("--") -> {
                println("⚠️ Unknown option: $arg (ignored)")
            }
            else -> positionalArgs += arg
        }
        i += 1
    }

    // 1. 获取项目根目录
    // 优先级：--cwd > 位置参数 > 当前工作目录
    val projectRoot = when {
        projectRootOverride != null -> projectRootOverride!!
        positionalArgs.isNotEmpty() -> File(positionalArgs[0])
        else -> File(System.getProperty("user.dir"))
    }

    println("📂 Project root: $projectRoot")

    // 1.0 配置日志输出到 <project>/.log 目录（包含 websocket 专用日志）
    try {
        StandaloneLogging.configure(projectRoot)
    } catch (e: Exception) {
        System.err.println("⚠️ Failed to configure logging: ${e.message}")
    }

    // 1.1 解析端口（支持 --port=XXXX / --port XXXX / 第二个位置参数）
    // Standalone 模式下默认使用固定端口 8765（便于前端开发时固定后端地址）
    val preferredPort = preferredPortOverride
        ?: when {
            positionalArgs.getOrNull(1)?.toIntOrNull() != null -> positionalArgs[1].toInt()
            else -> 8765  // 默认端口
        }

    preferredPort?.let {
        println("🔌 Preferred port from CLI: $it")
    }

    // 2. 创建默认的 IDE 工具实现（传入项目路径）
    val defaultIdeTools = com.asakii.server.tools.IdeToolsDefault(projectRoot.absolutePath)
    println("🔧 Using Default IdeTools with project path: ${projectRoot.absolutePath}")

    // 3. 创建协程作用域
    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // 4. 定位前端 dist 目录（开发模式下可选）
    val frontendDistDir = projectRoot.resolve("frontend/dist").toPath()
    val devMode = !Files.exists(frontendDistDir)

    if (devMode) {
        println("⚠️ Frontend dist directory not found: $frontendDistDir")
        println("🔧 Running in DEV mode - frontend should be served separately (e.g., npm run dev)")
        println("💡 Backend will only provide WebSocket and API endpoints")
    } else {
        println("📂 Using frontend directory: $frontendDistDir")
    }

    // 5. 实例化 HttpApiServer
    val server = HttpApiServer(
        ideTools = defaultIdeTools,
        scope = scope,
        frontendDir = if (devMode) null else frontendDistDir
    )

    // 6. 启动服务器并打印 URL
    try {
        val url = server.start(preferredPort = preferredPort)
        println("✅ Server started successfully at: $url")
        println("💡 Open this URL in your browser to test the chat interface.")
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
    }
}

