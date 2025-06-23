package com.claudecodeplus.core

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
 * Logger 工厂
 */
object LoggerFactory {
    fun getLogger(clazz: Class<*>): SimpleLogger {
        return JavaLogger(clazz.name)
    }
    
    fun getLogger(name: String): SimpleLogger {
        return JavaLogger(name)
    }
}