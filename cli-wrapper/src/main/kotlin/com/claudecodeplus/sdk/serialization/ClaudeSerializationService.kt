package com.claudecodeplus.sdk.serialization

import com.claudecodeplus.sdk.Tool
import com.claudecodeplus.sdk.ToolParser
import kotlinx.serialization.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Claude 序列化服务
 * 
 * 统一的消息序列化和反序列化服务，整合了消息解析、工具调用处理等功能
 * 基于 Claudia 项目的成功实现模式
 */
class ClaudeSerializationService {
    
    private val logger = LoggerFactory.getLogger(ClaudeSerializationService::class.java)
    
    // 工具调用映射表：工具调用 ID -> 工具调用请求
    private val pendingToolCalls = ConcurrentHashMap<String, ToolCallMapping>()
    
    /**
     * 工具调用映射
     * 将工具调用请求和响应关联起来
     */
    data class ToolCallMapping(
        val request: ToolUseRequest,
        val tool: Tool,
        var response: ToolUseResponse? = null,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * 处理单条 JSONL 消息
     * 解析消息并处理工具调用映射
     * 
     * @param jsonLine JSONL 行内容
     * @return 处理结果
     */
    fun processMessage(jsonLine: String): MessageProcessingResult? {
        val parsed = ClaudeMessageParser.parseMessage(jsonLine) ?: return null
        
        // 处理工具调用请求
        parsed.toolUseRequests.forEach { request ->
            val tool = ToolParser.parse(request.name, request.input)
            pendingToolCalls[request.id] = ToolCallMapping(request, tool)
            logger.debug("Registered tool call: ${request.name} (${request.id})")
        }
        
        // 处理工具调用响应
        parsed.toolUseResponses.forEach { response ->
            val mapping = pendingToolCalls[response.toolUseId]
            if (mapping != null) {
                mapping.response = response
                logger.debug("Completed tool call: ${mapping.tool.name} (${response.toolUseId})")
            } else {
                logger.warn("Received tool response for unknown tool call: ${response.toolUseId}")
            }
        }
        
        return MessageProcessingResult(
            parsedMessage = parsed,
            toolCalls = parsed.toolUseRequests.mapNotNull { request ->
                pendingToolCalls[request.id]
            }
        )
    }
    
    /**
     * 批量处理 JSONL 消息
     * 
     * @param jsonlContent 完整的 JSONL 内容
     * @return 处理结果列表
     */
    fun processMessages(jsonlContent: String): List<MessageProcessingResult> {
        val results = mutableListOf<MessageProcessingResult>()
        
        jsonlContent.lines().forEach { line ->
            processMessage(line)?.let { result ->
                results.add(result)
            }
        }
        
        return results
    }
    
    /**
     * 获取所有待处理的工具调用
     * 
     * @return 工具调用映射列表
     */
    fun getPendingToolCalls(): List<ToolCallMapping> {
        return pendingToolCalls.values.toList()
    }
    
    /**
     * 获取已完成的工具调用
     * 
     * @return 已完成的工具调用映射列表
     */
    fun getCompletedToolCalls(): List<ToolCallMapping> {
        return pendingToolCalls.values.filter { it.response != null }
    }
    
    /**
     * 获取指定工具调用的详细信息
     * 
     * @param toolCallId 工具调用 ID
     * @return 工具调用映射或 null
     */
    fun getToolCall(toolCallId: String): ToolCallMapping? {
        return pendingToolCalls[toolCallId]
    }
    
    /**
     * 清理过期的工具调用映射
     * 
     * @param maxAgeMs 最大年龄（毫秒）
     */
    fun cleanupExpiredToolCalls(maxAgeMs: Long = 300_000L) { // 5分钟
        val now = System.currentTimeMillis()
        val expiredKeys = pendingToolCalls.entries.filter { (_, mapping) ->
            now - mapping.timestamp > maxAgeMs
        }.map { it.key }
        
        expiredKeys.forEach { key ->
            pendingToolCalls.remove(key)
            logger.debug("Cleaned up expired tool call: $key")
        }
        
        if (expiredKeys.isNotEmpty()) {
            logger.info("Cleaned up ${expiredKeys.size} expired tool calls")
        }
    }
    
    /**
     * 序列化消息回 JSONL 格式
     * 
     * @param parsedMessage 已解析的消息
     * @return JSONL 字符串
     */
    fun serializeMessage(parsedMessage: ParsedMessage): String {
        return ClaudeMessageSerializer.serializeMessage(parsedMessage)
    }
    
    /**
     * 创建用户消息
     * 
     * @param content 消息内容
     * @param sessionId 会话 ID
     * @param parentUuid 父消息 UUID
     * @param cwd 当前工作目录
     * @param contexts 上下文列表
     * @return JSONL 字符串
     */
    fun createUserMessage(
        content: String,
        sessionId: String,
        parentUuid: String? = null,
        cwd: String? = null,
        contexts: List<ContextBlock> = emptyList()
    ): String {
        return ClaudeMessageSerializer.createUserMessage(
            content = content,
            sessionId = sessionId,
            parentUuid = parentUuid,
            cwd = cwd,
            contexts = contexts
        )
    }
    
    /**
     * 创建工具结果消息
     * 
     * @param toolUseId 工具使用 ID
     * @param result 结果内容
     * @param isError 是否为错误
     * @param sessionId 会话 ID
     * @param parentUuid 父消息 UUID
     * @return JSONL 字符串
     */
    fun createToolResultMessage(
        toolUseId: String,
        result: String,
        isError: Boolean = false,
        sessionId: String,
        parentUuid: String? = null
    ): String {
        return ClaudeMessageSerializer.createToolResultMessage(
            toolUseId = toolUseId,
            result = result,
            isError = isError,
            sessionId = sessionId,
            parentUuid = parentUuid
        )
    }
    
    /**
     * 重置服务状态
     * 清除所有待处理的工具调用
     */
    fun reset() {
        val count = pendingToolCalls.size
        pendingToolCalls.clear()
        logger.info("Reset serialization service, cleared $count tool call mappings")
    }
    
    /**
     * 获取服务统计信息
     * 
     * @return 统计信息
     */
    fun getStatistics(): SerializationStatistics {
        val completedCount = pendingToolCalls.values.count { it.response != null }
        val pendingCount = pendingToolCalls.size - completedCount
        
        return SerializationStatistics(
            totalToolCalls = pendingToolCalls.size,
            completedToolCalls = completedCount,
            pendingToolCalls = pendingCount
        )
    }
}

/**
 * 消息处理结果
 * 封装消息解析结果和相关的工具调用信息
 */
data class MessageProcessingResult(
    val parsedMessage: ParsedMessage,
    val toolCalls: List<ClaudeSerializationService.ToolCallMapping>
)

/**
 * 序列化统计信息
 */
data class SerializationStatistics(
    val totalToolCalls: Int,
    val completedToolCalls: Int,
    val pendingToolCalls: Int
)