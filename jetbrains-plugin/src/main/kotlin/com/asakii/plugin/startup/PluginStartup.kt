package com.asakii.plugin.startup

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.asakii.plugin.config.PluginConfig
import com.intellij.openapi.diagnostic.Logger

/**
 * 插件启动活动（动态插件兼容版本）
 *
 * 使用 ProjectActivity 替代已废弃的 StartupActivity，
 * 不修改全局系统属性，支持动态加载/卸载。
 */
class PluginStartup : ProjectActivity {

    companion object {
        private val logger = Logger.getInstance(PluginStartup::class.java)
    }

    override suspend fun execute(project: Project) {
        try {
            // 仅执行必要的初始化，不修改全局系统属性
            PluginConfig.setupEnvironment()
            logger.info("插件启动初始化完成")
        } catch (e: Exception) {
            logger.error("插件启动初始化失败", e)
        }
    }
}
