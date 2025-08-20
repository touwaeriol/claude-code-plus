package com.claudecodeplus.plugin.config

import java.io.File

/**
 * 插件配置
 */
object PluginConfig {
    
    /**
     * 获取 Claude SDK wrapper 脚本路径
     */
    fun getClaudeSdkWrapperPath(): String {
        // 开发模式下，从项目根目录查找
        val projectRoot = findProjectRoot()
        if (projectRoot != null) {
            val scriptFile = File(projectRoot, "cli-wrapper/claude-sdk-wrapper.js")
            if (scriptFile.exists()) {
                return scriptFile.absolutePath
            }
        }
        
        // 尝试从系统属性获取
        System.getProperty("claude.sdk.wrapper.path")?.let {
            return it
        }
        
        // 默认路径
        return "/Users/erio/codes/idea/claude-code-plus/cli-wrapper/claude-sdk-wrapper.js"
    }
    
    /**
     * 查找项目根目录
     */
    private fun findProjectRoot(): String? {
        // 从当前类的位置向上查找
        val classLocation = PluginConfig::class.java.protectionDomain.codeSource?.location?.path
        if (classLocation != null) {
            var currentDir = File(classLocation).parentFile
            repeat(10) {
                if (currentDir == null) return@repeat
                
                // 检查是否包含 settings.gradle.kts
                if (File(currentDir, "settings.gradle.kts").exists() ||
                    File(currentDir, "settings.gradle").exists()) {
                    return currentDir.absolutePath
                }
                
                // 检查是否包含 cli-wrapper 目录
                if (File(currentDir, "cli-wrapper").isDirectory) {
                    return currentDir.absolutePath
                }
                
                currentDir = currentDir.parentFile
            }
        }
        
        return null
    }
    
    /**
     * 设置环境变量，确保 Node.js 脚本能找到
     */
    fun setupEnvironment() {
        val scriptPath = getClaudeSdkWrapperPath()
        val scriptDir = File(scriptPath).parentFile.absolutePath
        
        // 设置系统属性，供 ClaudeCliWrapper 使用
        System.setProperty("claude.project.root", File(scriptDir).parent)
        System.setProperty("claude.sdk.wrapper.path", scriptPath)
        
        println("[PluginConfig] Claude SDK Wrapper 路径: $scriptPath")
        println("[PluginConfig] 项目根目录: ${File(scriptDir).parent}")
    }
}