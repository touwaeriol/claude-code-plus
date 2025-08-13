package com.claudecodeplus.ui.services

import com.claudecodeplus.sdk.ClaudeCliWrapper
import com.claudecodeplus.sdk.SDKMessage
import com.claudecodeplus.sdk.MessageType
import com.claudecodeplus.sdk.MessageData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * ClaudeCliWrapper 的适配器
 * 
 * 保持原有的流式 API 接口，但内部使用新的简化架构
 * 这样可以避免大量的代码更改
 */
class ClaudeCliWrapperAdapter(
    private val unifiedService: UnifiedSessionService,
    private val scope: CoroutineScope
) {
    private val realWrapper = ClaudeCliWrapper()
    
    /**
     * 执行查询，返回模拟的流式响应
     */
    suspend fun query(prompt: String, options: ClaudeCliWrapper.QueryOptions): Flow<SDKMessage> = flow {
        try {
            // 发送开始消息
            emit(SDKMessage(
                type = MessageType.START,
                data = MessageData(sessionId = options.resume)
            ))
            
            // 执行实际的 CLI 命令
            val result = realWrapper.query(prompt, options)
            
            if (result.success) {
                // 模拟成功消息
                emit(SDKMessage(
                    type = MessageType.TEXT,
                    data = MessageData(text = "命令执行成功")
                ))
                
                emit(SDKMessage(
                    type = MessageType.END,
                    data = MessageData(sessionId = result.sessionId)
                ))
            } else {
                // 发送错误消息
                emit(SDKMessage(
                    type = MessageType.ERROR,
                    data = MessageData(error = result.errorMessage ?: "未知错误")
                ))
            }
            
        } catch (e: Exception) {
            emit(SDKMessage(
                type = MessageType.ERROR,
                data = MessageData(error = e.message ?: "执行失败")
            ))
        }
    }
    
    /**
     * 终止当前操作
     */
    fun terminate() {
        realWrapper.terminate()
    }
    
    /**
     * 检查进程是否运行
     */
    fun isProcessAlive(): Boolean {
        return realWrapper.isProcessAlive()
    }
    
    /**
     * 检查 Claude Code SDK 是否可用
     */
    suspend fun isClaudeCliAvailable(): Boolean {
        return realWrapper.isClaudeCodeSdkAvailable()
    }
}