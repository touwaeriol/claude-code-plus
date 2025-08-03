package com.claudecodeplus.ui.services

import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.ConcurrentHashMap

/**
 * UnifiedSessionService 提供者
 * 
 * 管理多个项目的 UnifiedSessionService 实例，确保每个项目路径对应一个独立的服务实例。
 * 当项目切换时，能够正确返回对应项目的会话服务。
 * 
 * 主要功能：
 * - 为每个项目路径创建独立的 UnifiedSessionService 实例
 * - 缓存和复用已创建的服务实例
 * - 自动清理不再使用的服务实例
 * - 线程安全的服务管理
 */
class UnifiedSessionServiceProvider(
    private val scope: CoroutineScope
) {
    /**
     * 项目路径到 UnifiedSessionService 的映射
     * 使用 ConcurrentHashMap 确保线程安全
     */
    private val serviceCache = ConcurrentHashMap<String, UnifiedSessionService>()
    
    /**
     * 获取指定项目路径的 UnifiedSessionService
     * 
     * @param projectPath 项目路径，如果为空或空白则使用当前工作目录
     * @return 对应项目的 UnifiedSessionService 实例
     */
    fun getServiceForProject(projectPath: String?): UnifiedSessionService {
        val normalizedPath = projectPath?.takeIf { it.isNotBlank() } 
            ?: System.getProperty("user.dir")
        
        return serviceCache.computeIfAbsent(normalizedPath) { path ->
            UnifiedSessionService(scope, path)
        }
    }
    
    /**
     * 获取当前默认的 UnifiedSessionService
     * 使用系统当前工作目录
     */
    fun getDefaultService(): UnifiedSessionService {
        return getServiceForProject(null)
    }
    
    /**
     * 清理指定项目的服务实例
     * 
     * @param projectPath 要清理的项目路径
     */
    fun clearServiceForProject(projectPath: String) {
        serviceCache.remove(projectPath)?.let { service ->
            service.shutdown()
        }
    }
    
    /**
     * 清理所有缓存的服务实例
     */
    fun clearAllServices() {
        serviceCache.values.forEach { service ->
            service.shutdown()
        }
        serviceCache.clear()
    }
    
    /**
     * 获取当前缓存的服务数量
     */
    fun getCachedServiceCount(): Int = serviceCache.size
    
    /**
     * 获取所有缓存的项目路径
     */
    fun getCachedProjectPaths(): Set<String> = serviceCache.keys.toSet()
}