package com.claudecodeplus.sdk

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.openapi.diagnostic.thisLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.apache.commons.io.input.Tailer
import org.apache.commons.io.input.TailerListener
import org.apache.commons.io.input.TailerListenerAdapter
import java.nio.file.Path
import java.util.concurrent.Executors
import kotlin.io.path.exists
import kotlin.io.path.fileSize

/**
 * 基于 Apache Commons IO Tailer 的会话监听器
 * 
 * 优势：
 * - 自动处理文件轮转
 * - 更低的CPU占用
 * - 内置错误恢复机制
 * - 支持文件从头读取
 */
class TailerSessionWatcher : SessionWatcher {
    private val logger = thisLogger()
    private val objectMapper = jacksonObjectMapper()
    
    private var tailer: Tailer? = null
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "SessionTailer").apply {
            isDaemon = true
        }
    }
    
    private var lastFileSize = 0L
    private var isCompressed = false
    
    override fun watchSession(sessionFile: Path): Flow<ClaudeSessionManager.SessionMessage> = callbackFlow {
        if (!sessionFile.exists()) {
            logger.warn("Session file not found: $sessionFile")
            close()
            return@callbackFlow
        }
        
        lastFileSize = sessionFile.fileSize()
        
        val listener = object : TailerListenerAdapter() {
            override fun handle(line: String) {
                if (line.isBlank()) return
                
                try {
                    val message = objectMapper.readValue<ClaudeSessionManager.SessionMessage>(line)
                    launch {
                        send(message)
                    }
                } catch (e: Exception) {
                    logger.warn("Failed to parse session line: $line", e)
                }
            }
            
            override fun fileRotated() {
                logger.info("Session file rotated")
                isCompressed = true
                lastFileSize = 0
            }
            
            override fun fileNotFound() {
                logger.warn("Session file not found during monitoring")
            }
            
            override fun handle(ex: Exception) {
                logger.error("Error in session tailer", ex)
            }
        }
        
        // 创建 Tailer
        // 参数：文件、监听器、延迟时间(ms)、从文件末尾开始、重新打开文件、缓冲区大小
        tailer = Tailer.create(
            sessionFile.toFile(),
            listener,
            500, // 500ms 延迟
            true, // 从文件末尾开始
            true, // 文件被删除后重新打开
            4096  // 缓冲区大小
        )
        
        // 在专用线程中运行
        executor.submit {
            tailer?.run()
        }
        
        // 定期检查文件大小，检测压缩
        val sizeCheckJob = launch {
            while (isActive) {
                delay(1000)
                try {
                    val currentSize = sessionFile.fileSize()
                    if (currentSize < lastFileSize) {
                        logger.info("Detected file compression: $lastFileSize -> $currentSize")
                        handleFileCompression(sessionFile)
                    }
                    lastFileSize = currentSize
                } catch (e: Exception) {
                    logger.debug("Error checking file size", e)
                }
            }
        }
        
        awaitClose {
            sizeCheckJob.cancel()
            stop()
        }
    }
    
    private suspend fun FlowCollector<ClaudeSessionManager.SessionMessage>.handleFileCompression(sessionFile: Path) {
        // 停止当前的 tailer
        tailer?.stop()
        
        // 重新读取整个文件
        try {
            sessionFile.toFile().readLines()
                .filter { it.isNotBlank() }
                .forEach { line ->
                    try {
                        val message = objectMapper.readValue<ClaudeSessionManager.SessionMessage>(line)
                        send(message.copy(compressed = true))
                    } catch (e: Exception) {
                        logger.warn("Failed to parse compressed line: $line", e)
                    }
                }
            
            logger.info("Reloaded compressed session file")
        } catch (e: Exception) {
            logger.error("Error handling file compression", e)
        }
        
        // 重新启动 tailer
        tailer?.run()
    }
    
    override fun stop() {
        tailer?.stop()
        tailer = null
    }
    
    override fun isWatching(): Boolean {
        return tailer != null
    }
    
    fun shutdown() {
        stop()
        executor.shutdown()
    }
}