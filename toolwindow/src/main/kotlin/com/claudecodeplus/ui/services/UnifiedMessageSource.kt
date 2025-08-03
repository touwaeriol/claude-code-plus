package com.claudecodeplus.ui.services

import com.claudecodeplus.ui.models.EnhancedMessage
import kotlinx.coroutines.flow.Flow

/**
 * 统一的消息源接口
 * 
 * 定义了获取会话消息的标准接口，支持历史消息加载和实时消息订阅
 * 可以有多种实现方式：基于文件的、基于 CLI 输出的等
 */
interface UnifiedMessageSource {
    
    /**
     * 加载会话的历史消息
     * 
     * @param sessionId 会话 ID
     * @param projectPath 项目路径
     * @param limit 限制返回的消息数量，null 表示返回所有
     * @return 历史消息列表
     */
    suspend fun loadHistoricalMessages(
        sessionId: String,
        projectPath: String,
        limit: Int? = null
    ): List<EnhancedMessage>
    
    /**
     * 订阅会话的新消息
     * 
     * @param sessionId 会话 ID
     * @param projectPath 项目路径
     * @return 新消息的 Flow
     */
    fun subscribeToNewMessages(
        sessionId: String,
        projectPath: String
    ): Flow<EnhancedMessage>
    
    /**
     * 检查会话是否存在
     * 
     * @param sessionId 会话 ID
     * @param projectPath 项目路径
     * @return 会话是否存在
     */
    suspend fun sessionExists(
        sessionId: String,
        projectPath: String
    ): Boolean
    
    /**
     * 获取会话的消息数量
     * 
     * @param sessionId 会话 ID
     * @param projectPath 项目路径
     * @return 消息数量
     */
    suspend fun getMessageCount(
        sessionId: String,
        projectPath: String
    ): Int
    
    /**
     * 清理资源
     * 
     * 停止监听、释放资源等
     */
    fun cleanup()
    
    /**
     * 获取消息源的类型
     */
    fun getSourceType(): MessageSourceType
}

/**
 * 消息源类型
 */
enum class MessageSourceType {
    /**
     * 基于文件监听的消息源
     */
    FILE_BASED,
    
    /**
     * 基于 CLI 输出的消息源（旧版，将被废弃）
     */
    CLI_OUTPUT,
    
    /**
     * 混合模式（过渡期使用）
     */
    HYBRID
}

/**
 * 消息源工厂
 */
class MessageSourceFactory(
    private val fileWatchService: SessionFileWatchService? = null
) {
    /**
     * 创建消息源
     * 
     * @param type 消息源类型
     * @param scope 协程作用域
     * @return 消息源实例
     */
    fun create(
        type: MessageSourceType,
        scope: kotlinx.coroutines.CoroutineScope
    ): UnifiedMessageSource {
        return when (type) {
            MessageSourceType.FILE_BASED -> {
                requireNotNull(fileWatchService) { 
                    "FileWatchService is required for FILE_BASED source" 
                }
                FileBasedMessageSource(fileWatchService, scope)
            }
            
            MessageSourceType.CLI_OUTPUT -> {
                // TODO: 实现基于 CLI 输出的消息源（用于向后兼容）
                throw NotImplementedError("CLI_OUTPUT source not yet implemented")
            }
            
            MessageSourceType.HYBRID -> {
                // TODO: 实现混合模式（同时支持两种方式）
                throw NotImplementedError("HYBRID source not yet implemented")
            }
        }
    }
}

/**
 * 消息源配置
 */
data class MessageSourceConfig(
    /**
     * 消息源类型
     */
    val sourceType: MessageSourceType = MessageSourceType.FILE_BASED,
    
    /**
     * 是否启用消息缓存
     */
    val enableCache: Boolean = true,
    
    /**
     * 缓存大小（消息数量）
     */
    val cacheSize: Int = 1000,
    
    /**
     * 是否启用消息去重
     */
    val enableDeduplication: Boolean = true,
    
    /**
     * 文件检查间隔（毫秒）
     */
    val fileCheckInterval: Long = 100,
    
    /**
     * 错误重试次数
     */
    val maxRetries: Int = 3,
    
    /**
     * 重试延迟（毫秒）
     */
    val retryDelay: Long = 1000
)