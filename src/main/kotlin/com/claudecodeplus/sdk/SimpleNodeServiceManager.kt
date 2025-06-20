package com.claudecodeplus.sdk

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * 简化版的 Node.js 服务管理器
 * 使用最基本的 ProcessBuilder 来启动 Node 进程
 */
class SimpleNodeServiceManager : Disposable {
    
    private val logger = thisLogger()
    private var nodeProcess: Process? = null
    private val socketPath = AtomicReference<String?>()
    private val extractDir = lazy { createTempDir() }
    
    companion object {
        private const val SERVICE_START_TIMEOUT = 30_000L // 30秒
    }
    
    /**
     * 启动 Node.js 服务
     */
    fun startService(project: Project): CompletableFuture<String?> {
        return CompletableFuture.supplyAsync {
            try {
                logger.info("=== Starting Simple Node Service Manager ===")
                
                // 1. 检查 Node.js
                if (!checkNode()) {
                    throw RuntimeException("Node.js not found")
                }
                
                // 2. 提取文件
                val serverDir = extractFiles()
                
                // 3. 启动进程
                val startJs = File(serverDir, "start.js")
                if (!startJs.exists()) {
                    throw RuntimeException("start.js not found: ${startJs.absolutePath}")
                }
                
                logger.info("Launching Node process...")
                logger.info("Command: node ${startJs.absolutePath}")
                logger.info("Working dir: ${project.basePath}")
                
                val pb = ProcessBuilder("node", startJs.absolutePath)
                pb.directory(project.basePath?.let { File(it) } ?: File("."))
                pb.environment()["NODE_ENV"] = "production"
                
                // 启动进程
                nodeProcess = pb.start()
                logger.info("Process started! PID: ${nodeProcess?.pid()}")
                
                // 4. 读取输出并查找 socket 路径
                val socketPathResult = readSocketPath()
                
                if (socketPathResult != null) {
                    socketPath.set(socketPathResult)
                    logger.info("✅ Service started successfully with socket: $socketPathResult")
                    socketPathResult
                } else {
                    throw RuntimeException("Failed to get socket path")
                }
                
            } catch (e: Exception) {
                logger.error("Failed to start service", e)
                null
            }
        }
    }
    
    /**
     * 检查 Node.js 是否可用
     */
    private fun checkNode(): Boolean {
        return try {
            val pb = ProcessBuilder("node", "--version")
            val process = pb.start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor(5, TimeUnit.SECONDS)
            
            logger.info("Node.js version: ${output.trim()}")
            process.exitValue() == 0
        } catch (e: Exception) {
            logger.error("Node.js check failed", e)
            false
        }
    }
    
    /**
     * 提取资源文件
     */
    private fun extractFiles(): File {
        val targetDir = File(extractDir.value, "server")
        targetDir.mkdirs()
        
        logger.info("Extracting files to: ${targetDir.absolutePath}")
        
        val files = listOf("start.js", "server.bundle.js")
        
        for (filename in files) {
            val resourcePath = "/claude-node/$filename"
            val targetFile = File(targetDir, filename)
            
            try {
                // 尝试多种方式获取资源
                var inputStream = javaClass.getResourceAsStream(resourcePath)
                
                if (inputStream == null) {
                    logger.warn("Resource not found with getResourceAsStream: $resourcePath")
                    
                    // 尝试类加载器
                    inputStream = javaClass.classLoader.getResourceAsStream("claude-node/$filename")
                    
                    if (inputStream == null) {
                        logger.warn("Resource not found with classLoader: claude-node/$filename")
                        
                        // 尝试当前线程的类加载器
                        inputStream = Thread.currentThread().contextClassLoader.getResourceAsStream("claude-node/$filename")
                        
                        if (inputStream == null) {
                            logger.error("Failed to find resource: $filename")
                            continue
                        }
                    }
                }
                
                inputStream.use { input ->
                    targetFile.outputStream().use { output ->
                        val bytes = input.copyTo(output)
                        logger.info("Extracted $filename: $bytes bytes")
                    }
                }
                
                if (filename == "start.js") {
                    targetFile.setExecutable(true)
                }
                
            } catch (e: Exception) {
                logger.error("Failed to extract $filename", e)
            }
        }
        
        return targetDir
    }
    
    /**
     * 读取 socket 路径
     */
    private fun readSocketPath(): String? {
        val process = nodeProcess ?: return null
        val startTime = System.currentTimeMillis()
        
        // 创建线程读取输出
        val outputReader = Thread {
            try {
                process.inputStream.bufferedReader().forEachLine { line ->
                    logger.info("[STDOUT] $line")
                    if (line.contains("SOCKET_PATH:")) {
                        val path = line.substringAfter("SOCKET_PATH:").trim()
                        if (path.isNotEmpty()) {
                            socketPath.set(path)
                        }
                    }
                }
            } catch (e: Exception) {
                logger.error("Error reading stdout", e)
            }
        }
        
        val errorReader = Thread {
            try {
                process.errorStream.bufferedReader().forEachLine { line ->
                    logger.warn("[STDERR] $line")
                }
            } catch (e: Exception) {
                logger.error("Error reading stderr", e)
            }
        }
        
        outputReader.start()
        errorReader.start()
        
        // 等待 socket 路径
        while (System.currentTimeMillis() - startTime < SERVICE_START_TIMEOUT) {
            val path = socketPath.get()
            if (path != null) {
                return path
            }
            
            if (!process.isAlive) {
                logger.error("Process died unexpectedly")
                return null
            }
            
            Thread.sleep(100)
        }
        
        logger.error("Timeout waiting for socket path")
        return null
    }
    
    /**
     * 创建临时目录
     */
    private fun createTempDir(): File {
        val tempDir = Files.createTempDirectory("claude-simple-${System.currentTimeMillis()}").toFile()
        tempDir.deleteOnExit()
        return tempDir
    }
    
    /**
     * 停止服务
     */
    fun stopService() {
        try {
            nodeProcess?.let { process ->
                if (process.isAlive) {
                    process.destroy()
                    process.waitFor(5, TimeUnit.SECONDS)
                }
            }
            logger.info("Service stopped")
        } catch (e: Exception) {
            logger.warn("Error stopping service", e)
        } finally {
            nodeProcess = null
            socketPath.set(null)
        }
    }
    
    fun getSocketPath(): String? = socketPath.get()
    
    fun isServiceRunning(): Boolean = nodeProcess?.isAlive == true && socketPath.get() != null
    
    override fun dispose() {
        stopService()
    }
}