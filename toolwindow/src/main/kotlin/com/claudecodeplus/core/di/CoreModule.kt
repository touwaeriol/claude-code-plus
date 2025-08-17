package com.claudecodeplus.core.di

import com.claudecodeplus.core.repositories.ProjectRepository
import com.claudecodeplus.core.repositories.impl.ProjectRepositoryImpl
import com.claudecodeplus.core.services.MessageProcessor
import com.claudecodeplus.core.services.ProjectService
import com.claudecodeplus.core.services.SessionService
import com.claudecodeplus.core.services.impl.MessageProcessorImpl
import com.claudecodeplus.core.services.impl.ProjectServiceImpl
import com.claudecodeplus.core.services.impl.SessionServiceImpl
import com.claudecodeplus.ui.models.LocalConfigManager

/**
 * 核心服务模块
 * 配置所有核心服务的依赖注入
 */
object CoreModule {
    
    /**
     * 配置所有服务的依赖注入
     */
    fun configure(container: ServiceContainer = ServiceContainer.instance) {
        // 配置基础服务
        configureBasicServices(container)
        
        // 配置仓库层
        configureRepositories(container)
        
        // 配置服务层
        configureServices(container)
    }
    
    /**
     * 配置基础服务
     */
    private fun configureBasicServices(container: ServiceContainer) {
        // LocalConfigManager 单例
        container.singleton<LocalConfigManager> {
            LocalConfigManager()
        }
    }
    
    /**
     * 配置仓库层
     */
    private fun configureRepositories(container: ServiceContainer) {
        // ProjectRepository 单例
        container.singleton<ProjectRepository> {
            ProjectRepositoryImpl(
                configManager = container.get<LocalConfigManager>()
            )
        }
    }
    
    /**
     * 配置服务层
     */
    private fun configureServices(container: ServiceContainer) {
        // MessageProcessor 单例
        container.singleton<MessageProcessor> {
            MessageProcessorImpl()
        }
        
        // SessionService 单例
        container.singleton<SessionService> {
            SessionServiceImpl(
                messageProcessor = container.get<MessageProcessor>()
            )
        }
        
        // ProjectService 单例
        container.singleton<ProjectService> {
            ProjectServiceImpl(
                projectRepository = container.get<ProjectRepository>()
            )
        }
    }
    
    /**
     * 获取所有已注册的服务列表
     */
    fun getRegisteredServices(): List<String> {
        return listOf(
            LocalConfigManager::class.simpleName,
            ProjectRepository::class.simpleName,
            MessageProcessor::class.simpleName,
            SessionService::class.simpleName,
            ProjectService::class.simpleName
        ).filterNotNull()
    }
}