package com.claudecodeplus.core.logging

import com.intellij.openapi.diagnostic.Logger as IdeaLogger

/**
 * 统一日志接口
 */
interface Logger {
    fun debug(tag: String, message: String, throwable: Throwable? = null)
    fun info(tag: String, message: String, throwable: Throwable? = null)
    fun warn(tag: String, message: String, throwable: Throwable? = null)
    fun error(tag: String, message: String, throwable: Throwable? = null)
    fun isDebugEnabled(): Boolean
}

/**
 * IntelliJ Platform Logger 实现
 * 使用 IDE 的日志系统，支持日志级别控制和持久化
 */
class IdeaPlatformLogger : Logger {
    private val loggers = mutableMapOf<String, IdeaLogger>()

    private fun getLogger(tag: String): IdeaLogger {
        return loggers.getOrPut(tag) {
            IdeaLogger.getInstance("#com.claudecodeplus.$tag")
        }
    }

    override fun debug(tag: String, message: String, throwable: Throwable?) {
        val logger = getLogger(tag)
        if (logger.isDebugEnabled) {
            if (throwable != null) {
                logger.debug("$message", throwable)
            } else {
                logger.debug(message)
            }
        }
    }

    override fun info(tag: String, message: String, throwable: Throwable?) {
        val logger = getLogger(tag)
        if (throwable != null) {
            logger.info("$message", throwable)
        } else {
            logger.info(message)
        }
    }

    override fun warn(tag: String, message: String, throwable: Throwable?) {
        val logger = getLogger(tag)
        if (throwable != null) {
            logger.warn("$message", throwable)
        } else {
            logger.warn(message)
        }
    }

    override fun error(tag: String, message: String, throwable: Throwable?) {
        val logger = getLogger(tag)
        if (throwable != null) {
            logger.error("$message", throwable)
        } else {
            logger.error(message)
        }
    }

    override fun isDebugEnabled(): Boolean {
        // 检查任意一个logger的debug状态
        return loggers.values.firstOrNull()?.isDebugEnabled ?: IdeaLogger.getInstance("#com.claudecodeplus").isDebugEnabled
    }
}

/**
 * 控制台日志实现（仅用于测试或非IDE环境）
 */
class ConsoleLogger : Logger {
    var debugEnabled = System.getProperty("claudecodeplus.debug", "false") == "true"

    override fun debug(tag: String, message: String, throwable: Throwable?) {
        if (debugEnabled) {
            println("[$tag] DEBUG: $message")
            throwable?.printStackTrace()
        }
    }

    override fun info(tag: String, message: String, throwable: Throwable?) {
        println("[$tag] INFO: $message")
        throwable?.printStackTrace()
    }

    override fun warn(tag: String, message: String, throwable: Throwable?) {
        println("[$tag] WARN: $message")
        throwable?.printStackTrace()
    }

    override fun error(tag: String, message: String, throwable: Throwable?) {
        System.err.println("[$tag] ERROR: $message")
        throwable?.printStackTrace()
    }

    override fun isDebugEnabled(): Boolean = debugEnabled
}

/**
 * 日志扩展函数，简化使用
 */
inline fun <reified T> T.logD(message: String, throwable: Throwable? = null) {
    LoggerProvider.logger.debug(T::class.simpleName ?: "Unknown", message, throwable)
}

inline fun <reified T> T.logI(message: String, throwable: Throwable? = null) {
    LoggerProvider.logger.info(T::class.simpleName ?: "Unknown", message, throwable)
}

inline fun <reified T> T.logW(message: String, throwable: Throwable? = null) {
    LoggerProvider.logger.warn(T::class.simpleName ?: "Unknown", message, throwable)
}

inline fun <reified T> T.logE(message: String, throwable: Throwable? = null) {
    LoggerProvider.logger.error(T::class.simpleName ?: "Unknown", message, throwable)
}

/**
 * 全局日志提供者
 */
object LoggerProvider {
    var logger: Logger = try {
        // 尝试使用 IntelliJ Platform Logger
        Class.forName("com.intellij.openapi.diagnostic.Logger")
        IdeaPlatformLogger()
    } catch (e: ClassNotFoundException) {
        // 回退到控制台日志（用于测试环境）
        ConsoleLogger()
    }
        private set

    fun setLogger(newLogger: Logger) {
        logger = newLogger
    }

    fun isDebugEnabled(): Boolean = logger.isDebugEnabled()
}

/**
 * 独立的日志函数，支持在没有接收者的情况下调用
 */
fun logD(message: String, throwable: Throwable? = null) {
    LoggerProvider.logger.debug("Global", message, throwable)
}

fun logI(message: String, throwable: Throwable? = null) {
    LoggerProvider.logger.info("Global", message, throwable)
}

fun logW(message: String, throwable: Throwable? = null) {
    LoggerProvider.logger.warn("Global", message, throwable)
}

fun logE(message: String, throwable: Throwable? = null) {
    LoggerProvider.logger.error("Global", message, throwable)
}
