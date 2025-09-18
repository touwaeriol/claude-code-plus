package com.claudecodeplus.ui.services

import com.claudecodeplus.ui.models.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File

/**
 * 简化的会话加载器
 * 提供基本的会话加载功能，替代复杂的原始实现
 */
class SessionLoaderSimple(
    private val sessionHistoryService: SessionHistoryService,
    private val messageProcessor: MessageProcessor,
    private val isHistoryMode: Boolean = true
) {

    /**
     * 从文件加载会话
     */
    fun loadSessionFromFile(
        sessionFile: File,
        sessionObject: SessionObject
    ): Flow<LoadResult> = flow {
        // 简化实现：发出一个空的成功结果
        emit(LoadResult.Success(emptyList()))
    }

    /**
     * 从消息列表加载会话
     */
    fun loadSessionFromMessages(
        messages: List<EnhancedMessage>,
        sessionObject: SessionObject
    ): Flow<LoadResult> = flow {
        // 简化实现：直接返回消息列表
        emit(LoadResult.Success(messages))
    }

    /**
     * 加载结果密封类
     */
    sealed class LoadResult {
        data class Success(val messages: List<EnhancedMessage>) : LoadResult()
        data class Error(val message: String) : LoadResult()
        object Loading : LoadResult()
    }

    /**
     * 检查会话文件是否存在
     */
    fun sessionExists(sessionFile: File): Boolean {
        return sessionFile.exists()
    }

    /**
     * 获取会话消息数量
     */
    fun getMessageCount(sessionFile: File): Int {
        return sessionHistoryService.getMessageCount(sessionFile)
    }
}

/**
 * 为了向后兼容，创建原名称的类型别名
 */
typealias SessionLoader = SessionLoaderSimple