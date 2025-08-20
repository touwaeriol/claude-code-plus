package com.claudecodeplus.plugin.startup

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.claudecodeplus.plugin.config.PluginConfig
import com.intellij.openapi.diagnostic.Logger

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
            
            // 设置插件环境
            PluginConfig.setupEnvironment()
            
            logger.info("插件启动初始化完成")
            logger.info("文件编码: ${System.getProperty("file.encoding")}")
            logger.info("Claude SDK 路径: ${PluginConfig.getClaudeSdkWrapperPath()}")
            
        } catch (e: Exception) {
            logger.error("插件启动初始化失败", e)
        }
    }
}