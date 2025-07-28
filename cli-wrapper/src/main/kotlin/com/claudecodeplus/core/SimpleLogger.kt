package com.claudecodeplus.core

import mu.KLogger
import mu.KotlinLogging

/**
 * 简单的日志接口，用于在非IDEA环境中使用
 */
interface SimpleLogger {
    fun debug(message: String)
    fun info(message: String)
    fun warn(message: String)
    fun warn(message: String, throwable: Throwable)
    fun error(message: String, throwable: Throwable)
}

/**
 * 使用 Java 标准日志的实现
 */
class JavaLogger(private val name: String) : SimpleLogger {
    private val logger = java.util.logging.Logger.getLogger(name)
    
    override fun debug(message: String) {
        logger.fine(message)
    }
    
    override fun info(message: String) {
        logger.info(message)
    }
    
    override fun warn(message: String) {
        logger.warning(message)
    }
    
    override fun warn(message: String, throwable: Throwable) {
        logger.log(java.util.logging.Level.WARNING, message, throwable)
    }
    
    override fun error(message: String, throwable: Throwable) {
        logger.log(java.util.logging.Level.SEVERE, message, throwable)
    }
}

/**
 * 使用 Kotlin Logging 的实现
 * 
 * 这是推荐的实现，提供了更好的性能和 Kotlin 风格的 API。
 * 通过 SLF4J 和 Logback，支持文件日志、自动滚动等高级功能。
 */
class KotlinLogger(private val kLogger: KLogger) : SimpleLogger {
    override fun debug(message: String) {
        kLogger.debug { message }
    }
    
    override fun info(message: String) {
        kLogger.info { message }
    }
    
    override fun warn(message: String) {
        kLogger.warn { message }
    }
    
    override fun warn(message: String, throwable: Throwable) {
        kLogger.warn(throwable) { message }
    }
    
    override fun error(message: String, throwable: Throwable) {
        kLogger.error(throwable) { message }
    }
}

/**
 * Logger 工厂
 * 
 * 默认使用 Kotlin Logging，提供文件日志和自动滚动功能。
 * 在初始化时，应该先调用 LogbackConfigurator.initialize()
 */
object LoggerFactory {
    init {
        // 初始化 Logback 配置
        try {
            LogbackConfigurator.initialize()
        } catch (e: Exception) {
            // 如果初始化失败，仍然可以使用默认配置
            println("日志配置初始化失败: ${e.message}")
        }
    }
    
    fun getLogger(clazz: Class<*>): SimpleLogger {
        return KotlinLogger(KotlinLogging.logger(clazz.name))
    }
    
    fun getLogger(name: String): SimpleLogger {
        return KotlinLogger(KotlinLogging.logger(name))
    }
}