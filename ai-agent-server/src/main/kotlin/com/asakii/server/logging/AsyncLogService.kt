package com.asakii.server.logging

import mu.KLogger
import org.slf4j.event.Level
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 异步日志服务
 *
 * 将消息格式化延迟到后台日志线程执行，避免阻塞工作线程。
 * 工作线程只需将格式化函数放入队列，后台线程负责实际的格式化和记录。
 *
 * 使用方式：
 * ```kotlin
 * AsyncLogService.info(logger) { formatMyMessage(message) }
 * ```
 */
object AsyncLogService {

    /**
     * 日志条目
     */
    private data class LogEntry(
        val logger: KLogger,
        val level: Level,
        val messageSupplier: () -> String,
        val throwable: Throwable? = null
    )

    // 日志队列（无界队列）
    private val queue = LinkedBlockingQueue<LogEntry>()

    // 运行状态
    private val running = AtomicBoolean(true)

    // 后台日志线程
    private val logThread = Thread({
        while (running.get() || queue.isNotEmpty()) {
            try {
                val entry = queue.poll(100, TimeUnit.MILLISECONDS) ?: continue
                processLogEntry(entry)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            } catch (e: Exception) {
                // 日志线程不应该崩溃
                e.printStackTrace()
            }
        }
        // 处理剩余的日志
        drainQueue()
    }, "AsyncLogService").apply {
        isDaemon = true
        start()
    }

    /**
     * 处理日志条目
     */
    private fun processLogEntry(entry: LogEntry) {
        try {
            // 在日志线程中执行格式化
            val message = entry.messageSupplier()

            when (entry.level) {
                Level.TRACE -> {
                    if (entry.throwable != null) entry.logger.trace(entry.throwable) { message }
                    else entry.logger.trace { message }
                }
                Level.DEBUG -> {
                    if (entry.throwable != null) entry.logger.debug(entry.throwable) { message }
                    else entry.logger.debug { message }
                }
                Level.INFO -> {
                    if (entry.throwable != null) entry.logger.info(entry.throwable) { message }
                    else entry.logger.info { message }
                }
                Level.WARN -> {
                    if (entry.throwable != null) entry.logger.warn(entry.throwable) { message }
                    else entry.logger.warn { message }
                }
                Level.ERROR -> {
                    if (entry.throwable != null) entry.logger.error(entry.throwable) { message }
                    else entry.logger.error { message }
                }
            }
        } catch (e: Exception) {
            // 格式化失败时记录原始错误
            entry.logger.error(e) { "Failed to format log message" }
        }
    }

    /**
     * 清空队列中的日志
     */
    private fun drainQueue() {
        while (queue.isNotEmpty()) {
            val entry = queue.poll() ?: break
            processLogEntry(entry)
        }
    }

    /**
     * 记录 TRACE 级别日志（格式化在日志线程执行）
     */
    fun trace(logger: KLogger, message: () -> String) {
        if (logger.isTraceEnabled) {
            queue.offer(LogEntry(logger, Level.TRACE, message))
        }
    }

    /**
     * 记录 DEBUG 级别日志（格式化在日志线程执行）
     */
    fun debug(logger: KLogger, message: () -> String) {
        if (logger.isDebugEnabled) {
            queue.offer(LogEntry(logger, Level.DEBUG, message))
        }
    }

    /**
     * 记录 INFO 级别日志（格式化在日志线程执行）
     */
    fun info(logger: KLogger, message: () -> String) {
        if (logger.isInfoEnabled) {
            queue.offer(LogEntry(logger, Level.INFO, message))
        }
    }

    /**
     * 记录 WARN 级别日志（格式化在日志线程执行）
     */
    fun warn(logger: KLogger, message: () -> String) {
        if (logger.isWarnEnabled) {
            queue.offer(LogEntry(logger, Level.WARN, message))
        }
    }

    /**
     * 记录 WARN 级别日志（带异常，格式化在日志线程执行）
     */
    fun warn(logger: KLogger, throwable: Throwable, message: () -> String) {
        if (logger.isWarnEnabled) {
            queue.offer(LogEntry(logger, Level.WARN, message, throwable))
        }
    }

    /**
     * 记录 ERROR 级别日志（格式化在日志线程执行）
     */
    fun error(logger: KLogger, message: () -> String) {
        if (logger.isErrorEnabled) {
            queue.offer(LogEntry(logger, Level.ERROR, message))
        }
    }

    /**
     * 记录 ERROR 级别日志（带异常，格式化在日志线程执行）
     */
    fun error(logger: KLogger, throwable: Throwable, message: () -> String) {
        if (logger.isErrorEnabled) {
            queue.offer(LogEntry(logger, Level.ERROR, message, throwable))
        }
    }

    /**
     * 关闭服务
     */
    fun shutdown() {
        running.set(false)
        logThread.interrupt()
        try {
            logThread.join(5000)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    /**
     * 获取队列大小（用于监控）
     */
    fun queueSize(): Int = queue.size
}

/**
 * KLogger 扩展函数：异步记录 INFO 日志（格式化在日志线程执行）
 */
fun KLogger.asyncInfo(message: () -> String) {
    AsyncLogService.info(this, message)
}

/**
 * KLogger 扩展函数：异步记录 DEBUG 日志（格式化在日志线程执行）
 */
fun KLogger.asyncDebug(message: () -> String) {
    AsyncLogService.debug(this, message)
}

/**
 * KLogger 扩展函数：异步记录 WARN 日志（格式化在日志线程执行）
 */
fun KLogger.asyncWarn(message: () -> String) {
    AsyncLogService.warn(this, message)
}

/**
 * KLogger 扩展函数：异步记录 ERROR 日志（格式化在日志线程执行）
 */
fun KLogger.asyncError(message: () -> String) {
    AsyncLogService.error(this, message)
}
