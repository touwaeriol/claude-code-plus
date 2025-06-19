package com.claudecodeplus.sdk

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.io.exists
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.concurrent.thread

/**
 * Claude SDK Wrapper 进程管理器
 */
@Service(Service.Level.APP)
class ClaudeSDKManager : Disposable {
    
    companion object {
        private val logger = Logger.getInstance(ClaudeSDKManager::class.java)
        private const val DEFAULT_PORT = 18080
        private const val DEFAULT_HOST = "127.0.0.1"
        private const val HEALTH_CHECK_TIMEOUT = 30000 // 30秒
        private const val HEALTH_CHECK_INTERVAL = 500 // 500ms
        
        @JvmStatic
        fun getInstance(): ClaudeSDKManager {
            return ApplicationManager.getApplication().getService(ClaudeSDKManager::class.java)
        }
    }
    
    private var wrapperProcess: Process? = null
    private var port: Int = DEFAULT_PORT
    private var host: String = DEFAULT_HOST
    private var isRunning = false
    private val processLock = Object()
    
    /**
     * 启动 SDK Wrapper
     */
    fun start(project: Project? = null): Boolean {
        synchronized(processLock) {
            if (isRunning) {
                logger.info("Claude SDK Wrapper 已在运行")
                return true
            }
            
            // 检查端口是否已被占用
            if (isPortInUse(port)) {
                logger.info("端口 $port 已被占用，检查是否为 SDK Wrapper...")
                if (isSDKWrapperRunning()) {
                    logger.info("检测到 SDK Wrapper 已在运行")
                    isRunning = true
                    return true
                } else {
                    // 尝试使用其他端口
                    port = findAvailablePort()
                    logger.info("使用新端口: $port")
                }
            }
            
            // 获取 Node.js 命令
            val nodeCommand = getNodeCommand()
            if (nodeCommand == null) {
                logger.error("无法获取 Node.js 命令")
                return false
            }
            
            // 构建完整命令
            val command = nodeCommand.toMutableList().apply {
                add(port.toString())
            }
            
            logger.info("启动 Claude SDK Wrapper: ${command.joinToString(" ")}")
            
            try {
                val processBuilder = ProcessBuilder(command)
                processBuilder.redirectErrorStream(true)
                
                // 设置工作目录为 Node.js 项目目录
                val wrapperDir = File(File(System.getProperty("user.dir")), "claude-sdk-wrapper-node")
                processBuilder.directory(wrapperDir)
                
                // 设置环境变量
                val env = processBuilder.environment()
                env["NODE_ENV"] = "production"
                
                // 启动进程
                wrapperProcess = processBuilder.start()
                
                // 启动日志读取线程
                startLogReader()
                
                // 等待服务启动
                if (waitForServiceReady()) {
                    isRunning = true
                    logger.info("Claude SDK Wrapper 启动成功")
                    return true
                } else {
                    logger.error("Claude SDK Wrapper 启动超时")
                    stop()
                    return false
                }
                
            } catch (e: Exception) {
                logger.error("启动 Claude SDK Wrapper 失败", e)
                stop()
                return false
            }
        }
    }
    
    /**
     * 停止 SDK Wrapper
     */
    fun stop() {
        synchronized(processLock) {
            wrapperProcess?.let { process ->
                try {
                    logger.info("停止 Claude SDK Wrapper...")
                    process.destroy()
                    
                    // 等待进程结束（最多5秒）
                    var waited = 0
                    while (process.isAlive && waited < 5000) {
                        Thread.sleep(100)
                        waited += 100
                    }
                    
                    // 如果还没结束，强制终止
                    if (process.isAlive) {
                        logger.warn("进程未正常结束，强制终止...")
                        process.destroyForcibly()
                    }
                    
                    logger.info("Claude SDK Wrapper 已停止")
                } catch (e: Exception) {
                    logger.error("停止进程时出错", e)
                }
            }
            
            wrapperProcess = null
            isRunning = false
        }
    }
    
    /**
     * 获取服务状态
     */
    fun isServiceRunning(): Boolean = isRunning && isSDKWrapperRunning()
    
    /**
     * 获取服务 URL
     */
    fun getServiceUrl(): String = "http://$host:$port"
    
    override fun dispose() {
        stop()
    }
    
    /**
     * 获取 Node.js 命令
     */
    private fun getNodeCommand(): List<String>? {
        // 开发环境：使用启动脚本
        val projectRoot = File(System.getProperty("user.dir"))
        val wrapperDir = File(projectRoot, "claude-sdk-wrapper-node")
        val startScript = File(wrapperDir, "start.sh")
        
        if (startScript.exists()) {
            logger.info("使用启动脚本运行 Node.js 服务")
            
            // 检查 node_modules 是否存在
            val nodeModules = File(wrapperDir, "node_modules")
            if (!nodeModules.exists()) {
                logger.error("请先在 claude-sdk-wrapper-node 目录运行 npm install")
                return null
            }
            
            // 使用启动脚本
            return listOf("/bin/bash", startScript.absolutePath)
        }
        
        // TODO: 生产环境支持打包后的可执行文件
        logger.error("找不到启动脚本")
        return null
    }
    
    /**
     * 检查文件是否需要更新
     */
    private fun needsUpdate(file: File): Boolean {
        // 简单实现：检查文件大小是否为0
        return file.length() == 0L
    }
    
    /**
     * 检查端口是否被占用
     */
    private fun isPortInUse(port: Int): Boolean {
        return try {
            ServerSocket(port).use { false }
        } catch (e: Exception) {
            true
        }
    }
    
    /**
     * 查找可用端口
     */
    private fun findAvailablePort(): Int {
        for (p in DEFAULT_PORT..DEFAULT_PORT + 100) {
            if (!isPortInUse(p)) {
                return p
            }
        }
        // 如果找不到，让系统分配
        return ServerSocket(0).use { it.localPort }
    }
    
    /**
     * 检查是否为 SDK Wrapper 服务
     */
    private fun isSDKWrapperRunning(): Boolean {
        return try {
            val url = URL("http://$host:$port/health")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 1000
            connection.readTimeout = 1000
            
            val responseCode = connection.responseCode
            if (responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                response.contains("\"status\":\"ok\"") || response.contains("Claude Unified Server")
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 等待服务就绪
     */
    private fun waitForServiceReady(): Boolean {
        val startTime = System.currentTimeMillis()
        
        while (System.currentTimeMillis() - startTime < HEALTH_CHECK_TIMEOUT) {
            if (isSDKWrapperRunning()) {
                return true
            }
            
            // 检查进程是否还在运行
            wrapperProcess?.let { process ->
                if (!process.isAlive) {
                    logger.error("进程已退出，退出码: ${process.exitValue()}")
                    return false
                }
            }
            
            Thread.sleep(HEALTH_CHECK_INTERVAL.toLong())
        }
        
        return false
    }
    
    /**
     * 启动日志读取线程
     */
    private fun startLogReader() {
        wrapperProcess?.let { process ->
            thread(name = "Claude-SDK-Log-Reader") {
                try {
                    BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            logger.info("[SDK Wrapper] $line")
                        }
                    }
                } catch (e: Exception) {
                    // 进程结束时会抛出异常，这是正常的
                    if (isRunning) {
                        logger.error("读取日志时出错", e)
                    }
                }
            }
        }
    }
}