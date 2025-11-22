package com.claudecodeplus.plugin.startup

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.claudecodeplus.plugin.config.PluginConfig
import com.intellij.openapi.diagnostic.Logger
import java.util.logging.LogManager
import java.io.InputStream
import java.nio.charset.StandardCharsets

/**
 * 插件启动活动
 * 在项目打开时执行初始化
 */
class PluginStartup : StartupActivity {
    
    companion object {
        private val logger = Logger.getInstance(PluginStartup::class.java)
    }
    
    override fun runActivity(project: Project) {
        try {
            // 设置系统编码
            System.setProperty("file.encoding", "UTF-8")
            System.setProperty("sun.jnu.encoding", "UTF-8")
            System.setProperty("console.encoding", "UTF-8")
            
            // 配置 java.util.logging 使用 UTF-8 编码
            try {
                val loggingConfigStream: InputStream? = 
                    javaClass.classLoader.getResourceAsStream("logging.properties")
                if (loggingConfigStream != null) {
                    LogManager.getLogManager().readConfiguration(loggingConfigStream)
                    loggingConfigStream.close()
                }
            } catch (e: Exception) {
                // 如果配置失败，继续执行（某些环境可能不支持）
                logger.warn("无法加载 logging.properties: ${e.message}")
            }
            
            // 设置标准输出流编码为 UTF-8
            try {
                System.setOut(java.io.PrintStream(System.out, true, StandardCharsets.UTF_8))
                System.setErr(java.io.PrintStream(System.err, true, StandardCharsets.UTF_8))
            } catch (e: Exception) {
                // 如果设置失败，继续执行（某些环境可能不支持）
                logger.warn("无法设置标准输出流编码: ${e.message}")
            }
            
            // 设置插件环境
            PluginConfig.setupEnvironment()
            
            logger.info("插件启动初始化完成")
            logger.info("文件编码: ${System.getProperty("file.encoding")}")
            logger.info("Claude 命令状态: ${System.getProperty("claude.command.check.result", "未检查")}")
            
        } catch (e: Exception) {
            logger.error("插件启动初始化失败", e)
        }
    }
}