package com.claudecodeplus.sdk

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.util.Key
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Node.js 服务管理器
 * 负责管理内嵌的 Node.js Claude SDK 服务的生命周期
 */
@Service(Service.Level.APP)
class NodeServiceManager : Disposable {
    
    private val logger = thisLogger()
    private var nodeProcess: Process? = null
    private var socketPath: String? = null
    private val extractDir = lazy { createSecureTempDir() }
    
    companion object {
        fun getInstance(): NodeServiceManager = service()
        private const val HEALTH_CHECK_TIMEOUT = 30_000L // 30秒
        private const val SERVICE_START_TIMEOUT = 60_000L // 60秒
    }
    
    /**
     * 启动 Node.js 服务
     */
    fun startService(project: Project): CompletableFuture<Int> {
        return CompletableFuture.supplyAsync {
            try {
                logger.info("Starting Node.js Claude SDK service...")
                
                // 1. 检查系统 Node.js 环境
                checkNodeEnvironment()
                
                // 2. Node 服务会自动创建 Unix Socket
                
                // 3. 提取服务代码
                val serverPath = extractServerCode()
                
                // 验证文件是否存在
                val startJsFile = File(serverPath, "start.js")
                val bundleFile = File(serverPath, "server.bundle.js")
                logger.info("Checking files:")
                logger.info("  start.js exists: ${startJsFile.exists()}, path: ${startJsFile.absolutePath}")
                logger.info("  start.js size: ${if (startJsFile.exists()) startJsFile.length() else "N/A"} bytes")
                logger.info("  start.js readable: ${if (startJsFile.exists()) startJsFile.canRead() else "N/A"}")
                logger.info("  start.js executable: ${if (startJsFile.exists()) startJsFile.canExecute() else "N/A"}")
                logger.info("  server.bundle.js exists: ${bundleFile.exists()}, path: ${bundleFile.absolutePath}")
                logger.info("  server.bundle.js size: ${if (bundleFile.exists()) bundleFile.length() else "N/A"} bytes")
                
                if (!startJsFile.exists()) {
                    throw RuntimeException("start.js not found at: ${startJsFile.absolutePath}")
                }
                
                // 读取 start.js 的前几行进行验证
                if (startJsFile.exists()) {
                    try {
                        val firstLines = startJsFile.readLines().take(5)
                        logger.info("First 5 lines of start.js:")
                        firstLines.forEachIndexed { index, line ->
                            logger.info("  Line ${index + 1}: $line")
                        }
                    } catch (e: Exception) {
                        logger.error("Failed to read start.js content", e)
                    }
                }
                
                // 4. 启动 Node 服务（使用打包后的文件）
                logger.info("Starting Node.js process...")
                logger.info("Working directory: ${project.basePath ?: "."}")
                logger.info("Server path: $serverPath")
                
                // 使用 IntelliJ 平台的 GeneralCommandLine
                val commandLine = GeneralCommandLine().apply {
                    exePath = "node"
                    addParameter(startJsFile.absolutePath)
                    workDirectory = project.basePath?.let { File(it) } ?: File(".")
                    
                    // 设置环境变量
                    environment["NODE_ENV"] = "production"
                    environment["NODE_PATH"] = File(serverPath, "node_modules").absolutePath
                    
                    // 设置字符集
                    charset = Charsets.UTF_8
                }
                
                logger.info("Command line: ${commandLine.commandLineString}")
                logger.info("Work directory: ${commandLine.workDirectory}")
                
                // 创建进程处理器
                val processHandler = OSProcessHandler(commandLine)
                
                // 启动进程
                processHandler.startNotify()
                nodeProcess = processHandler.process
                
                logger.info("Node process started, PID: ${nodeProcess?.pid()}")
                
                // 添加进程监听器来捕获输出
                processHandler.addProcessListener(object : ProcessListener {
                    override fun startNotified(event: ProcessEvent) {
                        logger.info("Process start notified")
                    }
                    
                    override fun processTerminated(event: ProcessEvent) {
                        logger.info("Process terminated with exit code: ${event.exitCode}")
                    }
                    
                    override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                        val text = event.text.trim()
                        if (text.isNotEmpty()) {
                            when (outputType) {
                                ProcessOutputTypes.STDOUT -> {
                                    logger.info("[Node Stdout] $text")
                                    if (text.contains("SOCKET_PATH:")) {
                                        val path = text.substringAfter("SOCKET_PATH:").trim()
                                        logger.info("Socket path detected: $path")
                                    }
                                }
                                ProcessOutputTypes.STDERR -> {
                                    if (text.startsWith("ERROR:")) {
                                        val errorJson = text.substringAfter("ERROR:")
                                        try {
                                            val errorInfo = parseErrorInfo(errorJson)
                                            logger.error("[Node Error] Stage: ${errorInfo.stage}, Message: ${errorInfo.message}")
                                        } catch (e: Exception) {
                                            logger.error("[Node Error] $errorJson")
                                        }
                                    } else {
                                        logger.warn("[Node Stderr] $text")
                                    }
                                }
                            }
                        }
                    }
                })
                
                // 启动后立即开始读取输出
                val reader = nodeProcess!!.inputStream.bufferedReader()
                val errorReader = nodeProcess!!.errorStream.bufferedReader()
                
                // 启动线程读取错误输出
                Thread {
                    try {
                        var line: String?
                        while (errorReader.readLine().also { line = it } != null) {
                            // 检查是否是结构化的错误信息
                            if (line!!.startsWith("ERROR:")) {
                                val errorJson = line!!.substringAfter("ERROR:")
                                
                                // 尝试解析错误详情并格式化输出
                                try {
                                    val errorInfo = parseErrorInfo(errorJson)
                                    logger.error("[Node Error] Stage: ${errorInfo.stage}, Message: ${errorInfo.message}")
                                } catch (e: Exception) {
                                    // 如果解析失败，直接输出原始 JSON
                                    logger.error("[Node Error] $errorJson")
                                }
                            } else if (line!!.isNotBlank()) {
                                // 其他非空的 stderr 输出，一行一次打印
                                logger.warn("[Node Stderr] $line")
                            }
                        }
                    } catch (e: Exception) {
                        logger.debug("Error reading stderr", e)
                    }
                }.start()
                
                // 5. 等待服务启动并获取 socket 路径
                val socket = waitForSocketPath()
                if (socket != null) {
                    socketPath = socket
                    logger.info("Node.js Claude SDK service started successfully with socket: $socket")
                    0 // 返回 0 表示成功（不再使用端口）
                } else {
                    throw RuntimeException("Failed to start Node.js service - socket path not found")
                }
            } catch (e: Exception) {
                logger.error("Failed to start Node.js service", e)
                socketPath = null
                throw e
            }
        }
    }
    
    /**
     * 停止服务
     */
    fun stopService() {
        try {
            logger.info("Stopping Node.js service...")
            
            nodeProcess?.let { process ->
                // 优雅关闭
                if (process.isAlive) {
                    process.destroy()
                    if (!process.waitFor(5, TimeUnit.SECONDS)) {
                        logger.warn("Node.js process did not terminate gracefully, force killing...")
                        process.destroyForcibly()
                    }
                }
            }
            
            logger.info("Node.js service stopped")
        } catch (e: Exception) {
            logger.warn("Error stopping Node.js service", e)
        } finally {
            nodeProcess = null
            socketPath = null
            // 清理临时文件
            cleanupExtractDir()
        }
    }
    
    /**
     * 检查系统 Node.js 环境
     */
    private fun checkNodeEnvironment() {
        try {
            val versionProcess = ProcessBuilder("node", "--version").start()
            val exitCode = versionProcess.waitFor(5, TimeUnit.SECONDS)
            
            if (!exitCode || versionProcess.exitValue() != 0) {
                throw RuntimeException("Node.js not found or not working")
            }
            
            val version = versionProcess.inputStream.bufferedReader().readText().trim()
            logger.info("Found Node.js version: $version")
            
            // 检查版本兼容性
            val versionNumber = version.removePrefix("v").split(".")[0].toIntOrNull()
            if (versionNumber == null || versionNumber < 18) {
                throw RuntimeException("Node.js version $version is not supported. Minimum required: 18.0.0")
            }
            
            logger.info("Node.js environment check passed")
            
        } catch (e: Exception) {
            logger.error("Node.js environment check failed", e)
            throw RuntimeException(
                "Node.js 环境检查失败: ${e.message}\n" +
                "请确保已安装 Node.js 18.0.0 或更高版本，并且在 PATH 中可用。\n" +
                "下载地址: https://nodejs.org/"
            )
        }
    }
    
    /**
     * 提取服务代码和依赖
     */
    private fun extractServerCode(): File {
        val serverDir = File(extractDir.value, "server")
        
        if (serverDir.exists() && File(serverDir, "server.bundle.js").exists()) {
            logger.info("Server code already extracted: ${serverDir.absolutePath}")
            return serverDir
        }
        
        serverDir.mkdirs()
        
        logger.info("Extracting server code to: ${serverDir.absolutePath}")
        
        // 提取服务文件
        extractNodeServiceFiles(serverDir)
        
        return serverDir
    }
    
    /**
     * 提取 Node 服务文件
     */
    private fun extractNodeServiceFiles(targetDir: File) {
        // 使用 esbuild 打包后的文件列表
        val resourceFiles = listOf(
            "server.bundle.js",
            "start.js"
        )
        
        logger.info("Extracting Node service files to: ${targetDir.absolutePath}")
        
        // 提取主文件
        resourceFiles.forEach { filename ->
            val resourcePath = "/claude-node/$filename"
            logger.info("Attempting to extract resource: $resourcePath")
            
            val resourceStream = javaClass.getResourceAsStream(resourcePath)
            if (resourceStream != null) {
                val targetFile = File(targetDir, filename)
                targetFile.parentFile.mkdirs()
                
                var bytesWritten = 0L
                resourceStream.use { input ->
                    targetFile.outputStream().use { output ->
                        bytesWritten = input.copyTo(output)
                    }
                }
                
                // 设置可执行权限（对于 start.js）
                if (filename == "start.js") {
                    val execResult = targetFile.setExecutable(true)
                    logger.info("Set executable permission for $filename: $execResult")
                }
                
                logger.info("Extracted: $filename (${bytesWritten} bytes)")
                logger.info("  Target file exists: ${targetFile.exists()}")
                logger.info("  Target file size: ${targetFile.length()} bytes")
                logger.info("  Target file path: ${targetFile.absolutePath}")
            } else {
                logger.error("Resource not found: $resourcePath")
                
                // 尝试列出所有可用的资源
                try {
                    logger.info("Attempting to list available resources:")
                    val url = javaClass.getResource("/claude-node/")
                    logger.info("Resource URL: $url")
                    
                    // 尝试另一种方式
                    val classLoader = javaClass.classLoader
                    logger.info("Using classloader: ${classLoader.javaClass.name}")
                    
                    // 尝试直接访问不带前导斜杠的路径
                    val altStream = classLoader.getResourceAsStream("claude-node/$filename")
                    if (altStream != null) {
                        logger.info("Found resource using classloader without leading slash!")
                        altStream.close()
                    }
                } catch (e: Exception) {
                    logger.error("Error listing resources", e)
                }
            }
        }
    }
    
    /**
     * 等待获取 socket 路径
     */
    private fun waitForSocketPath(): String? {
        val startTime = System.currentTimeMillis()
        val reader = nodeProcess?.inputStream?.bufferedReader() ?: return null
        
        // 使用 CompletableFuture 来等待 socket 路径
        val socketPathFuture = CompletableFuture<String?>()
        
        // 启动一个线程来持续读取输出
        val readThread = Thread {
            try {
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    logger.info("[Node Stdout] $line")
                    
                    // 查找 socket 路径输出
                    if (line!!.contains("SOCKET_PATH:")) {
                        val path = line!!.substringAfter("SOCKET_PATH:").trim()
                        if (path.isNotEmpty()) {
                            logger.info("Found socket path: $path")
                            socketPathFuture.complete(path)
                            break
                        }
                    }
                }
                // 如果读取结束还没找到，完成 future
                socketPathFuture.complete(null)
            } catch (e: Exception) {
                logger.error("Error reading Node output", e)
                socketPathFuture.completeExceptionally(e)
            }
        }
        readThread.start()
        
        try {
            // 等待 socket 路径或超时
            val path = socketPathFuture.get(SERVICE_START_TIMEOUT, TimeUnit.MILLISECONDS)
            if (path != null) {
                return path
            }
        } catch (e: Exception) {
            logger.error("Error waiting for socket path", e)
        }
        
        // 检查进程是否还在运行
        if (nodeProcess?.isAlive != true) {
            logger.error("Node process terminated unexpectedly")
            val exitCode = try {
                nodeProcess?.exitValue()
            } catch (e: Exception) {
                null
            }
            logger.error("Node process exit code: $exitCode")
            return null
        }
        
        logger.error("Timeout waiting for socket path after ${SERVICE_START_TIMEOUT}ms")
        return null
    }
    
    /**
     * 解析错误信息
     */
    private fun parseErrorInfo(errorJson: String): ErrorInfo {
        // 简单的 JSON 解析
        val stage = errorJson.substringAfter("\"stage\":\"").substringBefore("\"")
        val message = errorJson.substringAfter("\"message\":\"").substringBefore("\"")
        return ErrorInfo(stage, message)
    }
    
    data class ErrorInfo(val stage: String, val message: String)
    
    /**
     * 创建安全的临时目录
     */
    private fun createSecureTempDir(): File {
        val tempDir = Files.createTempDirectory("claude-node-${System.currentTimeMillis()}").toFile()
        tempDir.deleteOnExit()
        
        // 设置权限（仅当前用户可访问）
        try {
            tempDir.setReadable(false, false)
            tempDir.setWritable(false, false)
            tempDir.setExecutable(false, false)
            tempDir.setReadable(true, true)
            tempDir.setWritable(true, true)
            tempDir.setExecutable(true, true)
        } catch (e: Exception) {
            logger.warn("Failed to set directory permissions", e)
        }
        
        return tempDir
    }
    
    /**
     * 清理临时目录
     */
    private fun cleanupExtractDir() {
        if (extractDir.isInitialized()) {
            try {
                extractDir.value.deleteRecursively()
                logger.info("Cleaned up temporary directory")
            } catch (e: Exception) {
                logger.warn("Failed to cleanup temporary directory", e)
            }
        }
    }
    
    /**
     * 获取 socket 路径
     */
    fun getSocketPath(): String? = socketPath
    
    /**
     * 检查服务是否运行
     */
    fun isServiceRunning(): Boolean {
        return nodeProcess?.isAlive == true && socketPath != null
    }
    
    /**
     * 健康检查
     */
    fun healthCheck(): Boolean {
        if (!isServiceRunning()) return false
        
        return try {
            // Unix Socket 通过检查进程和 socket 文件来验证健康状态
            val socket = socketPath ?: return false
            File(socket).exists() && nodeProcess?.isAlive == true
        } catch (e: Exception) {
            logger.debug("Health check failed", e)
            false
        }
    }
    
    override fun dispose() {
        stopService()
    }
}