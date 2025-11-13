package com.claudecodeplus.server.services

import com.claudecodeplus.sdk.ClaudeCodeSdkClient
import com.claudecodeplus.sdk.types.*
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

/**
 * Claude 会话管理器
 *
 * 负责管理多个会话的 SDK 客户端实例，提供客户端池和资源管理。
 *
 * 主要功能：
 * - 客户端池管理：为每个会话创建和维护独立的 SDK 客户端
 * - 会话隔离：每个 sessionId 对应独立的对话上下文
 * - 资源管理：自动清理断开连接的会话资源（子进程、协程）
 * - 生命周期管理：客户端连接、断开、中断
 *
 * 设计参考：ClaudeCodeSdkAdapter（toolwindow 模块）
 */
object ClaudeSessionManager {
    private val logger = Logger.getLogger(ClaudeSessionManager::class.java.name)

    /**
     * 会话ID到SDK客户端的映射
     * 每个会话维护独立的 Claude CLI 子进程
     */
    private val sessionClients = ConcurrentHashMap<String, ClaudeCodeSdkClient>()

    /**
     * 会话ID到协程作用域的映射
     * 用于管理每个会话的协程生命周期
     */
    private val sessionScopes = ConcurrentHashMap<String, CoroutineScope>()

    /**
     * 为指定会话创建或获取 SDK 客户端
     *
     * @param sessionId 会话ID
     * @param project IntelliJ 项目实例
     * @return SDK 客户端实例
     */
    suspend fun getOrCreateClient(
        sessionId: String,
        project: Project
    ): ClaudeCodeSdkClient {
        return sessionClients.getOrPut(sessionId) {
            logger.info("📱 创建会话 $sessionId 的 SDK 客户端")

            // 构建配置选项
            val options = buildClaudeOptions(project)

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
     * 初始化会话并连接 SDK 客户端
     *
     * 在 WebSocket 连接建立时调用，立即创建并连接 SDK 客户端
     *
     * @param sessionId 会话ID
     * @param project IntelliJ 项目实例
     */
    suspend fun initializeSession(
        sessionId: String,
        project: Project
    ) {
        logger.info("🎬 初始化会话 $sessionId")

        try {
            // 获取或创建客户端
            val client = getOrCreateClient(sessionId, project)

            // 立即连接
            if (!client.isConnected()) {
                logger.info("🔌 连接会话 $sessionId 的 SDK 客户端")
                client.connect()
                logger.info("✅ 会话 $sessionId 的 SDK 客户端已连接")
            } else {
                logger.info("ℹ️ 会话 $sessionId 的 SDK 客户端已经连接")
            }
        } catch (e: Exception) {
            logger.severe("❌ 初始化会话 $sessionId 失败: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    /**
     * 观察会话的所有 SDK 消息（持续流）
     *
     * 返回 SDK 的原始消息流，不会在 ResultMessage 后结束。
     * 用于 WebSocket 的独立消息监听协程。
     *
     * @param sessionId 会话ID
     * @return SDK 消息流（持续推送）
     */
    fun observeSessionMessages(sessionId: String): Flow<Message> {
        val client = sessionClients[sessionId]
            ?: throw IllegalStateException("会话 $sessionId 未初始化")

        logger.info("👀 开始观察会话 $sessionId 的消息流")

        // 直接返回 SDK 的底层消息流（从 ControlProtocol）
        // 这个流不会在 ResultMessage 后结束，会持续推送所有消息
        return client.getAllMessages()
            .onStart {
                logger.info("🎬 会话 $sessionId 的消息流已启动")
            }
            .onEach { message ->
                logger.info("📨 会话 $sessionId 消息: ${message::class.simpleName}")
            }
            .catch { error ->
                logger.severe("❌ 会话 $sessionId 消息流错误: ${error.message}")
                throw error
            }
    }

    /**
     * 只发送消息，不等待响应
     *
     * 用于解耦的消息发送，响应由 observeSessionMessages() 独立处理
     *
     * @param sessionId 会话ID
     * @param message 用户消息内容
     * @param project IntelliJ 项目实例（用于懒加载客户端）
     */
    suspend fun sendMessageOnly(
        sessionId: String,
        message: String,
        project: Project
    ) {
        logger.info("📤 发送消息到会话 $sessionId: ${message.take(50)}...")

        try {
            // 获取客户端（如果未初始化则创建并连接）
            val client = sessionClients[sessionId]
                ?: run {
                    logger.info("⚠️ 会话 $sessionId 未初始化，执行懒加载")
                    initializeSession(sessionId, project)
                    sessionClients[sessionId]!!
                }

            // 只发送消息，不等待响应
            client.query(message, sessionId)
            logger.info("✅ 消息已发送到会话 $sessionId")

        } catch (e: Exception) {
            logger.severe("❌ 发送消息失败: sessionId=$sessionId, error=${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    /**
     * 发送消息到指定会话
     *
     * @param sessionId 会话ID
     * @param message 用户消息内容
     * @param project IntelliJ 项目实例
     * @return SDK 消息流
     */
    suspend fun sendMessage(
        sessionId: String,
        message: String,
        project: Project
    ): Flow<Message> {
        logger.info("🚀 发送消息到会话 $sessionId: ${message.take(50)}...")

        try {
            // 获取或创建客户端
            val client = getOrCreateClient(sessionId, project)

            // 确保客户端已连接
            if (!client.isConnected()) {
                logger.info("🔌 连接会话 $sessionId 的 SDK 客户端")
                client.connect()
            }

            // 发送消息
            client.query(message, sessionId)

            // 返回响应流
            return client.receiveResponse()
                .onStart {
                    logger.info("🎬 会话 $sessionId 响应流开始")
                }
                .onEach { sdkMessage ->
                    logger.info("📨 会话 $sessionId 收到消息: ${sdkMessage::class.simpleName}")
                }
                .catch { error ->
                    when (error) {
                        is CancellationException -> {
                            logger.info("⚠️ 会话 $sessionId 操作被取消: ${error.message}")
                            throw error
                        }
                        else -> {
                            logger.severe("❌ 会话 $sessionId 消息处理错误: ${error.message}")
                            throw error
                        }
                    }
                }
        } catch (e: CancellationException) {
            logger.info("⚠️ 会话 $sessionId 发送操作被取消")
            throw e
        } catch (e: Exception) {
            logger.severe("❌ 会话 $sessionId 发送消息失败: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    /**
     * 中断指定会话的执行
     *
     * @param sessionId 会话ID
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
     * 关闭指定会话并清理资源
     *
     * 清理内容：
     * - 断开 SDK 客户端连接（终止子进程）
     * - 取消协程作用域
     * - 从客户端池移除
     *
     * @param sessionId 会话ID
     */
    suspend fun closeSession(sessionId: String) {
        logger.info("🚪 关闭会话 $sessionId")

        try {
            // 1. 断开并移除客户端
            val client = sessionClients.remove(sessionId)
            if (client != null) {
                client.disconnect()
                logger.info("✅ 会话 $sessionId 的 SDK 客户端已断开")
            }

            // 2. 取消并移除协程作用域
            val scope = sessionScopes.remove(sessionId)
            if (scope != null) {
                scope.cancel()
                logger.info("✅ 会话 $sessionId 的协程作用域已取消")
            }

            logger.info("✅ 会话 $sessionId 已完全关闭")
        } catch (e: Exception) {
            logger.severe("❌ 关闭会话 $sessionId 时出错: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * 检查会话是否活跃
     *
     * @param sessionId 会话ID
     * @return true 如果会话存在且已连接
     */
    fun isSessionActive(sessionId: String): Boolean {
        val client = sessionClients[sessionId]
        return client?.isConnected() == true
    }

    /**
     * 获取所有活跃会话的ID
     *
     * @return 会话ID集合
     */
    fun getActiveSessionIds(): Set<String> {
        return sessionClients.keys.toSet()
    }

    /**
     * 获取活跃会话数量
     *
     * @return 会话数量
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

        logger.info("✅ 所有会话已关闭，共 ${sessionIds.size} 个")
    }

    /**
     * 构建 Claude SDK 配置选项
     *
     * @param project IntelliJ 项目实例
     * @return SDK 配置选项
     */
    private fun buildClaudeOptions(project: Project): ClaudeAgentOptions {
        return ClaudeAgentOptions(
            model = "claude-sonnet-4-5-20250929",
            cwd = project.basePath?.let { java.nio.file.Path.of(it) },
            debugStderr = true,
            maxTurns = 50
        )
    }
}
