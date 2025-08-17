package com.claudecodeplus.core

import com.claudecodeplus.core.di.CoreModule
import com.claudecodeplus.core.di.ServiceContainer
import com.claudecodeplus.core.logging.ConsoleLogger
import com.claudecodeplus.core.logging.LoggerProvider
import com.claudecodeplus.core.logging.logI

/**
 * 应用程序初始化器
 * 负责配置依赖注入和初始化各种服务
 */
object ApplicationInitializer {
    
    private var isInitialized = false
    
    /**
     * 初始化应用程序
     */
    fun initialize() {
        if (isInitialized) {
            logI("应用程序已初始化，跳过")
            return
        }
        
        try {
            logI("开始初始化应用程序...")
            
            // 1. 配置日志系统
            setupLogging()
            
            // 2. 配置依赖注入
            setupDependencyInjection()
            
            // 3. 验证服务配置
            validateServices()
            
            isInitialized = true
            logI("应用程序初始化完成")
            
        } catch (e: Exception) {
            logI("应用程序初始化失败: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
    
    /**
     * 配置日志系统
     */
    private fun setupLogging() {
        logI("配置日志系统...")
        LoggerProvider.setLogger(ConsoleLogger())
        logI("日志系统配置完成")
    }
    
    /**
     * 配置依赖注入
     */
    private fun setupDependencyInjection() {
        logI("配置依赖注入...")
        CoreModule.configure()
        logI("依赖注入配置完成")
        
        // 打印已注册的服务
        val services = CoreModule.getRegisteredServices()
        logI("已注册 ${services.size} 个服务: ${services.joinToString(", ")}")
    }
    
    /**
     * 验证服务配置
     */
    private fun validateServices() {
        logI("验证服务配置...")
        
        val container = ServiceContainer.instance
        
        // 检查关键服务是否能正常创建
        val criticalServices = listOf(
            com.claudecodeplus.core.services.MessageProcessor::class,
            com.claudecodeplus.core.services.SessionService::class,
            com.claudecodeplus.core.services.ProjectService::class,
            com.claudecodeplus.core.repositories.ProjectRepository::class
        )
        
        criticalServices.forEach { serviceClass ->
            if (!container.isRegistered(serviceClass)) {
                throw IllegalStateException("关键服务未注册: ${serviceClass.simpleName}")
            }
            
            try {
                val instance = container.get(serviceClass)
                logI("✅ ${serviceClass.simpleName} 服务创建成功")
            } catch (e: Exception) {
                throw IllegalStateException("服务创建失败: ${serviceClass.simpleName}", e)
            }
        }
        
        logI("服务配置验证完成")
    }
    
    /**
     * 清理应用程序资源
     */
    fun cleanup() {
        if (!isInitialized) return
        
        logI("清理应用程序资源...")
        
        try {
            // 清理服务容器
            ServiceContainer.instance.clear()
            
            isInitialized = false
            logI("应用程序资源清理完成")
            
        } catch (e: Exception) {
            logI("清理应用程序资源失败: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * 检查是否已初始化
     */
    fun isInitialized(): Boolean = isInitialized
}