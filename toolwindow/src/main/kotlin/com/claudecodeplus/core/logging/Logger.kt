package com.claudecodeplus.core.logging

/**
 * 统一日志接口
 */
interface Logger {
    fun debug(tag: String, message: String, throwable: Throwable? = null)
    fun info(tag: String, message: String, throwable: Throwable? = null)
    fun warn(tag: String, message: String, throwable: Throwable? = null)
    fun error(tag: String, message: String, throwable: Throwable? = null)
}

/**
 * 控制台日志实现
 */
class ConsoleLogger : Logger {
    override fun debug(tag: String, message: String, throwable: Throwable?) {
        println("[$tag] DEBUG: $message")
        throwable?.printStackTrace()
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
        println("[$tag] ERROR: $message")
        throwable?.printStackTrace()
    }
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
    var logger: Logger = ConsoleLogger()
        private set
    
    fun setLogger(newLogger: Logger) {
        logger = newLogger
    }
}