package com.claudecodeplus.sdk

import com.claudecodeplus.core.LoggerFactory
import com.claudecodeplus.core.LogbackConfigurator
import mu.KotlinLogging
import org.junit.jupiter.api.Test

/**
 * 日志系统测试
 * 
 * 展示如何使用新的 Kotlin 日志系统
 */
class LoggingTest {
    
    // 使用 SimpleLogger 接口（兼容旧代码）
    private val simpleLogger = LoggerFactory.getLogger(LoggingTest::class.java)
    
    // 直接使用 Kotlin Logging（推荐）
    private val kLogger = KotlinLogging.logger {}
    
    @Test
    fun testLogging() {
        // 初始化日志系统（通常在应用启动时调用）
        LogbackConfigurator.initialize()
        
        // 使用 SimpleLogger 接口
        simpleLogger.debug("这是一条调试消息")
        simpleLogger.info("这是一条信息消息")
        simpleLogger.warn("这是一条警告消息")
        simpleLogger.error("这是一条错误消息", RuntimeException("测试异常"))
        
        // 使用 Kotlin Logging（更高效）
        kLogger.debug { "Kotlin Logging 调试消息" }
        kLogger.info { "Kotlin Logging 信息消息" }
        kLogger.warn { "Kotlin Logging 警告消息" }
        kLogger.error(RuntimeException("测试异常")) { "Kotlin Logging 错误消息" }
        
        // 惰性求值的优势
        val expensiveOperation = { 
            Thread.sleep(100) // 模拟耗时操作
            "这是一个耗时的字符串拼接结果"
        }
        
        // 只有在日志级别允许时才会执行 expensiveOperation
        kLogger.debug { "计算结果: ${expensiveOperation()}" }
        
        println("日志已写入到: ~/.claude-code-plus/logs/")
    }
    
    @Test
    fun testDynamicLogLevel() {
        // 动态调整日志级别
        LogbackConfigurator.setLogLevel("com.claudecodeplus", "DEBUG")
        
        simpleLogger.debug("现在可以看到调试日志了")
        
        // 恢复默认级别
        LogbackConfigurator.setLogLevel("com.claudecodeplus", "INFO")
    }
    
    @Test
    fun testCreateSampleConfig() {
        // 为用户创建示例配置文件
        LogbackConfigurator.createSampleConfig()
        
        println("示例配置文件已创建到: ~/.claude-code-plus/logback.xml")
    }
}