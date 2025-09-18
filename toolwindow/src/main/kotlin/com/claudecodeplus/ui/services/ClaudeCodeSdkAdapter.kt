package com.claudecodeplus.ui.services

import com.claudecodeplus.sdk.ClaudeCodeSdkClient
import com.claudecodeplus.sdk.types.*
import com.claudecodeplus.ui.models.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

/**
 * Claude Code SDK 适配器
 *
 * 替代 GlobalCliWrapper，提供基于 claude-code-sdk 的统一接口。
 * 负责管理多个会话的 SDK 客户端实例，处理消息流转和状态同步。
 *
 * 主要功能：
 * - 会话管理：为每个会话创建独立的 SDK 客户端
 * - 消息转换：SDK 消息 ↔ EnhancedMessage
 * - 回调分发：将消息分发到对应的会话回调
 * - 生命周期管理：客户端连接、断开、清理
 */
object ClaudeCodeSdkAdapter {
    private val logger = Logger.getLogger(ClaudeCodeSdkAdapter::class.java.name)

    /**
     * 会话ID到SDK客户端的映射
     */
    private val sessionClients = ConcurrentHashMap<String, ClaudeCodeSdkClient>()

    /**
     * 会话ID到消息回调的映射
     */
    private val sessionCallbacks = ConcurrentHashMap<String, (EnhancedMessage) -> Unit>()

    /**
     * 会话ID到协程作用域的映射，用于管理每个会话的协程
     */
    private val sessionScopes = ConcurrentHashMap<String, CoroutineScope>()

    /**
     * 为指定会话创建或获取 SDK 客户端
     */
    suspend fun getOrCreateClient(
        sessionId: String,
        sessionObject: SessionObject,
        project: Project? = null
    ): ClaudeCodeSdkClient {
        return sessionClients.getOrPut(sessionId) {
            logger.info("📱 为会话 $sessionId 创建新的 SDK 客户端")

            // 构建配置选项
            val options = SdkMessageConverter.buildClaudeCodeOptions(sessionObject, project)

            // 创建客户端
            val client = ClaudeCodeSdkClient(options)

            // 创建会话作用域
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            sessionScopes[sessionId] = scope

            logger.info("✅ 会话 $sessionId 的 SDK 客户端已创建")
            client
        }
    }

    /**
     * 为会话发送消息
     */
    suspend fun sendMessage(
        sessionId: String,
        message: EnhancedMessage,
        sessionObject: SessionObject,
        project: Project? = null
    ): Flow<EnhancedMessage> {
        println("🚀🚀🚀 [ClaudeCodeSdkAdapter] sendMessage 函数被调用!! sessionId=$sessionId, content=${message.content.take(50)}")
        logger.info("🚀 [ClaudeCodeSdkAdapter] sendMessage 被调用: sessionId=$sessionId, content=${message.content.take(50)}")

        try {
            println("🔧 [ClaudeCodeSdkAdapter] 尝试获取或创建客户端...")
            val client = getOrCreateClient(sessionId, sessionObject, project)

            // 确保客户端已连接
            if (!client.isConnected()) {
                println("🔌 [ClaudeCodeSdkAdapter] 连接会话 $sessionId 的 SDK 客户端")
                logger.info("🔌 连接会话 $sessionId 的 SDK 客户端")
                client.connect()
            }

            // 转换并发送消息
            println("📤 [ClaudeCodeSdkAdapter] 转换并发送消息...")
            val sdkUserMessage = SdkMessageConverter.toSdkUserMessage(message, sessionId)
            client.query(message.content, sessionId)

            // 返回响应流
            println("📬 [ClaudeCodeSdkAdapter] 返回响应流...")
            return client.receiveResponse()
                .onStart {
                    println("🎬 [ClaudeCodeSdkAdapter] 响应流开始...")
                    logger.info("🎬 会话 $sessionId 响应流开始")
                }
                .map { sdkMessage ->
                    println("📨 [ClaudeCodeSdkAdapter] 收到SDK原始消息: ${sdkMessage::class.simpleName}")
                    logger.info("📨 会话 $sessionId 收到 SDK 消息: ${sdkMessage::class.simpleName}")

                    val enhancedMessage = SdkMessageConverter.fromSdkMessage(sdkMessage, sessionObject)
                    println("✅ [ClaudeCodeSdkAdapter] 转换后的消息: role=${enhancedMessage.role}, content=${enhancedMessage.content.take(50)}")
                    logger.info("✅ 会话 $sessionId 转换后消息: role=${enhancedMessage.role}")

                    enhancedMessage
                }
                // 移除 onEach 回调分发，避免与 responseFlow.collect 重复处理
                // .onEach { enhancedMessage ->
                //     // 分发到会话回调
                //     sessionCallbacks[sessionId]?.invoke(enhancedMessage)
                // }
                .catch { error ->
                    when (error) {
                        is CancellationException -> {
                            // 协程取消是正常的生命周期事件，不是错误
                            println("⚠️ [ClaudeCodeSdkAdapter] 会话 $sessionId 操作被取消: ${error.message}")
                            logger.info("⚠️ 会话 $sessionId 操作被取消: ${error.message}")
                            // 重新抛出以保持协程语义
                            throw error
                        }
                        else -> {
                            logger.severe("❌ 会话 $sessionId 消息处理错误: ${error.message}")
                            emit(EnhancedMessage(
                                role = MessageRole.ERROR,
                                content = "处理消息时发生错误: ${error.message}",
                                isError = true,
                                status = MessageStatus.FAILED
                            ))
                        }
                    }
                }
        } catch (e: CancellationException) {
            // 协程取消是正常的生命周期事件，直接抛出
            println("⚠️ [ClaudeCodeSdkAdapter] sendMessage 操作被取消: ${e.message}")
            logger.info("⚠️ sendMessage 操作被取消: ${e.message}")
            throw e
        } catch (e: Exception) {
            println("❌❌❌ [ClaudeCodeSdkAdapter] sendMessage 异常: ${e.message}")
            logger.severe("❌ [ClaudeCodeSdkAdapter] sendMessage 异常: ${e.message}")
            e.printStackTrace()

            // 返回错误流
            return flow {
                emit(EnhancedMessage(
                    role = MessageRole.ERROR,
                    content = "发送消息失败: ${e.message}",
                    isError = true,
                    status = MessageStatus.FAILED
                ))
            }
        }
    }

    /**
     * 注册会话回调
     */
    fun registerSessionCallback(sessionId: String, callback: (EnhancedMessage) -> Unit) {
        sessionCallbacks[sessionId] = callback
        logger.info("📞 注册会话 $sessionId 的消息回调")
    }

    /**
     * 注销会话回调
     */
    fun unregisterSessionCallback(sessionId: String) {
        sessionCallbacks.remove(sessionId)
        logger.info("📞 注销会话 $sessionId 的消息回调")
    }

    /**
     * 中断会话
     */
    suspend fun interruptSession(sessionId: String) {
        val client = sessionClients[sessionId]
        if (client != null) {
            logger.info("⏹️ 中断会话 $sessionId")
            client.interrupt()
        } else {
            logger.warning("⚠️ 尝试中断不存在的会话: $sessionId")
        }
    }

    /**
     * 关闭指定会话
     */
    suspend fun closeSession(sessionId: String) {
        logger.info("🚪 关闭会话 $sessionId")

        // 移除回调
        sessionCallbacks.remove(sessionId)

        // 断开并移除客户端
        val client = sessionClients.remove(sessionId)
        client?.disconnect()

        // 取消并移除作用域
        val scope = sessionScopes.remove(sessionId)
        scope?.cancel()

        logger.info("✅ 会话 $sessionId 已关闭")
    }

    /**
     * 获取会话状态信息
     */
    fun getSessionInfo(sessionId: String): Map<String, Any>? {
        val client = sessionClients[sessionId]
        return client?.getServerInfo()
    }

    /**
     * 检查会话是否已连接
     */
    fun isSessionConnected(sessionId: String): Boolean {
        val client = sessionClients[sessionId]
        return client?.isConnected() == true
    }

    /**
     * 获取所有活跃会话ID
     */
    fun getActiveSessionIds(): Set<String> {
        return sessionClients.keys.toSet()
    }

    /**
     * 获取活跃会话数量
     */
    fun getActiveSessionCount(): Int {
        return sessionClients.size
    }

    /**
     * 关闭所有会话（应用关闭时调用）
     */
    suspend fun closeAllSessions() {
        logger.info("🚪 关闭所有会话")

        val sessionIds = sessionClients.keys.toList()
        sessionIds.forEach { sessionId ->
            closeSession(sessionId)
        }

        logger.info("✅ 所有会话已关闭")
    }

    /**
     * 为会话启动消息监听
     * 这个方法启动一个协程来持续监听 SDK 客户端的消息
     */
    fun startSessionMessageListening(
        sessionId: String,
        client: ClaudeCodeSdkClient,
        sessionObject: SessionObject,
        callback: (EnhancedMessage) -> Unit
    ) {
        val scope = sessionScopes[sessionId] ?: return

        scope.launch {
            try {
                client.receiveResponse().collect { sdkMessage ->
                    val enhancedMessage = SdkMessageConverter.fromSdkMessage(sdkMessage, sessionObject)
                    callback(enhancedMessage)
                }
            } catch (e: CancellationException) {
                // 协程取消是正常的生命周期事件，不需要报错
                logger.info("⚠️ 会话 $sessionId 消息监听被取消: ${e.message}")
                // 重新抛出以保持协程语义
                throw e
            } catch (e: Exception) {
                logger.severe("❌ 会话 $sessionId 消息监听异常: ${e.message}")
                callback(EnhancedMessage(
                    role = MessageRole.ERROR,
                    content = "消息监听异常: ${e.message}",
                    isError = true,
                    status = MessageStatus.FAILED
                ))
            }
        }
    }
}