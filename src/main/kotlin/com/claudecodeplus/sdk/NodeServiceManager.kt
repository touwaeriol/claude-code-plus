package com.claudecodeplus.sdk

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import java.io.File
import java.io.IOException
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Node.js 服务管理器
 * 负责管理内嵌的 Node.js Claude SDK 服务的生命周期
 */
@Service(Service.Level.APP)
class NodeServiceManager : Disposable {
    
    private val logger = thisLogger()
    private var nodeProcess: Process? = null
    private val serverPort = AtomicInteger(0)
    private val extractDir = lazy { createSecureTempDir() }
    
    companion object {
        fun getInstance(): NodeServiceManager = service()
        private const val DEFAULT_PORT = 18080
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
                
                // 2. 查找可用端口
                val port = findAvailablePort(DEFAULT_PORT)
                serverPort.set(port)
                
                // 3. 提取服务代码
                val serverPath = extractServerCode()
                
                // 4. 启动 Node 服务（使用系统 Node.js）
                val command = listOf(
                    "node",
                    File(serverPath, "dist/server.js").absolutePath,
                    "--port", port.toString(),
                    "--host", "127.0.0.1"
                )
                
                logger.info("Starting Node.js process: ${command.joinToString(" ")}")
                
                val processBuilder = ProcessBuilder(command)
                    .directory(project.basePath?.let { File(it) } ?: File("."))
                    .redirectErrorStream(true)
                
                // 设置环境变量
                processBuilder.environment().apply {
                    put("NODE_ENV", "production")
                    put("NODE_PATH", File(serverPath, "node_modules").absolutePath)
                }
                
                nodeProcess = processBuilder.start()
                
                // 5. 等待服务启动并进行健康检查
                if (waitForServiceReady(port)) {
                    logger.info("Node.js Claude SDK service started successfully on port $port")
                    port
                } else {
                    throw RuntimeException("Failed to start Node.js service - health check timeout")
                }
            } catch (e: Exception) {
                logger.error("Failed to start Node.js service", e)
                serverPort.set(0)
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
            serverPort.set(0)
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
            if (versionNumber == null || versionNumber < 14) {
                throw RuntimeException("Node.js version $version is not supported. Minimum required: 14.0.0")
            }
            
            logger.info("Node.js environment check passed")
            
        } catch (e: Exception) {
            logger.error("Node.js environment check failed", e)
            throw RuntimeException(
                "Node.js 环境检查失败: ${e.message}\n" +
                "请确保已安装 Node.js 14.0.0 或更高版本，并且在 PATH 中可用。\n" +
                "下载地址: https://nodejs.org/"
            )
        }
    }
    
    /**
     * 提取服务代码和依赖
     */
    private fun extractServerCode(): File {
        val serverDir = File(extractDir.value, "server")
        
        if (serverDir.exists() && File(serverDir, "dist/server.js").exists()) {
            logger.info("Server code already extracted: ${serverDir.absolutePath}")
            return serverDir
        }
        
        serverDir.mkdirs()
        
        logger.info("Extracting server code to: ${serverDir.absolutePath}")
        
        // 提取服务文件
        extractResourceRecursively("/claude-node/server/", serverDir)
        
        return serverDir
    }
    
    /**
     * 递归提取资源文件
     */
    private fun extractResourceRecursively(resourcePath: String, targetDir: File) {
        // 这里需要根据实际的资源结构来实现
        // 可以使用 ClassLoader 获取资源列表
        val resourcePaths = listOf(
            "package.json",
            "dist/server.js",
            "dist/services/claudeService.js",
            "dist/services/sessionManager.js",
            "dist/routes/httpRoutes.js",
            "dist/routes/wsHandlers.js"
            // 注意：node_modules 会在构建时打包进来
        )
        
        resourcePaths.forEach { path ->
            val resourceStream = javaClass.getResourceAsStream("$resourcePath$path")
            if (resourceStream != null) {
                val targetFile = File(targetDir, path)
                targetFile.parentFile.mkdirs()
                
                resourceStream.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
        
        // 特殊处理 node_modules（如果作为资源打包）
        extractNodeModules(targetDir)
    }
    
    /**
     * 提取 node_modules
     */
    private fun extractNodeModules(serverDir: File) {
        // 这里需要根据实际的打包方式来实现
        // 可能需要从压缩包或其他格式中提取
        val nodeModulesDir = File(serverDir, "node_modules")
        if (!nodeModulesDir.exists()) {
            nodeModulesDir.mkdirs()
            // TODO: 实现 node_modules 的提取逻辑
            logger.warn("node_modules extraction not yet implemented")
        }
    }
    
    
    /**
     * 等待服务准备就绪
     */
    private fun waitForServiceReady(port: Int): Boolean {
        val startTime = System.currentTimeMillis()
        val httpClient = HttpClient.newHttpClient()
        
        while (System.currentTimeMillis() - startTime < HEALTH_CHECK_TIMEOUT) {
            try {
                val response = httpClient.send(
                    HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:$port/health"))
                        .timeout(Duration.ofSeconds(2))
                        .GET()
                        .build(),
                    HttpResponse.BodyHandlers.ofString()
                )
                
                if (response.statusCode() == 200) {
                    return true
                }
            } catch (e: Exception) {
                // 继续等待
            }
            
            Thread.sleep(1000)
        }
        return false
    }
    
    /**
     * 查找可用端口
     */
    private fun findAvailablePort(startPort: Int): Int {
        for (port in startPort until startPort + 100) {
            try {
                ServerSocket(port).use { return port }
            } catch (e: IOException) {
                continue
            }
        }
        throw RuntimeException("No available port found starting from $startPort")
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
    fun getServicePort(): Int = serverPort.get()
    
    /**
     * 检查服务是否运行
     */
    fun isServiceRunning(): Boolean {
        return nodeProcess?.isAlive == true && serverPort.get() > 0
    }
    
    /**
     * 健康检查
     */
    fun healthCheck(): Boolean {
        if (!isServiceRunning()) return false
        
        return try {
            val port = getServicePort()
            val httpClient = HttpClient.newHttpClient()
            
            val request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:$port/health"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            response.statusCode() == 200
        } catch (e: Exception) {
            logger.debug("Health check failed", e)
            false
        }
    }
    
    override fun dispose() {
        stopService()
    }
}