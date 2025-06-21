package com.claudecodeplus.sdk

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.execution.configurations.GeneralCommandLine
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Node.js 服务管理器 - 已废弃
 * 现在直接使用 ClaudeCliWrapper 调用 claude 命令
 * 
 * @deprecated 使用 ClaudeCliWrapper 代替
 */
@Deprecated("使用 ClaudeCliWrapper 代替", ReplaceWith("ClaudeCliWrapper"))
@Service(Service.Level.APP)
class NodeServiceManager : Disposable {

    private val logger = thisLogger()
    private var nodeProcess: Process? = null
    private var servicePort: Int? = null
    private val extractDir = lazy { createSecureTempDir() }

    companion object {
        fun getInstance(): NodeServiceManager = service()
        private const val SERVICE_START_TIMEOUT = 60_000L // 60秒
    }

    /**
     * 启动 Node.js 服务
     * @return 服务端口
     */
    fun startService(project: Project): CompletableFuture<Int?> {
        return CompletableFuture.supplyAsync {
            try {
                logger.info("Starting Node.js Claude SDK service...")

                // 1. 检查系统 Node.js 环境
                checkNodeEnvironment()

                // 2. 提取服务代码
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

                // 创建一个 CompletableFuture 来等待端口
                val portFuture = CompletableFuture<Int?>()

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

                // 创建进程
                val process = commandLine.createProcess()
                nodeProcess = process

                logger.info("Node process started, PID: ${process.pid()}")

                // 启动输出流读取线程
                Thread {
                    try {
                        process.inputStream.bufferedReader().use { reader ->
                            reader.lines().forEach { line ->
                                logger.info("[Node STDOUT] $line")
                                
                                // 检测端口
                                if (line.contains("PORT:")) {
                                    val portStr = line.substringAfter("PORT:").trim()
                                    val port = portStr.toIntOrNull()
                                    if (port != null && !portFuture.isDone) {
                                        servicePort = port
                                        logger.info("Detected Node service port: $port")
                                        portFuture.complete(port)
                                    }
                                }
                                
                                // 记录 WebSocket 服务启动信息
                                if (line.contains("WebSocket server listening on port")) {
                                    logger.info("WebSocket server is ready!")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        logger.error("Error reading stdout", e)
                    }
                }.start()

                // 启动错误流读取线程
                Thread {
                    try {
                        process.errorStream.bufferedReader().use { reader ->
                            reader.lines().forEach { line ->
                                if (line.isNotEmpty()) {
                                    logger.error("[Node STDERR] $line")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        logger.error("Error reading stderr", e)
                    }
                }.start()

                // 5. 等待服务启动并获取端口
                val port = portFuture.get(SERVICE_START_TIMEOUT, TimeUnit.MILLISECONDS)
                if (port != null) {
                    servicePort = port
                    logger.info("Node.js Claude SDK service started successfully on port: $port")
                    port // 返回端口
                } else {
                    throw RuntimeException("Failed to start Node.js service - port not found")
                }
            } catch (e: Exception) {
                logger.error("Failed to start Node.js service", e)
                servicePort = null
                null // 返回 null 表示失败
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
            servicePort = null
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
     * 获取服务端口
     */
    fun getServicePort(): Int? = servicePort

    /**
     * 检查服务是否运行
     */
    fun isServiceRunning(): Boolean {
        return nodeProcess?.isAlive == true && servicePort != null
    }


    override fun dispose() {
        stopService()
    }
}