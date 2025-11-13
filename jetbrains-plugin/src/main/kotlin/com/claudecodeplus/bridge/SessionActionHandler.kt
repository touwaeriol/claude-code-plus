package com.claudecodeplus.bridge

import com.intellij.openapi.project.Project
import kotlinx.serialization.json.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

/**
 * 会话操作处理器
 * 负责处理前端的会话管理操作
 */
class SessionActionHandler(
    private val project: Project
) {
    private val logger = Logger.getLogger(javaClass.name)
    private val json = Json { ignoreUnknownKeys = true }

    // 简化的会话存储 - 项目级别的会话列表
    private val sessions = ConcurrentHashMap<String, SessionData>()
    private var currentSessionId: String? = null

    // 会话历史缓存 - sessionId -> messages
    private val historyCache = ConcurrentHashMap<String, List<JsonObject>>()

    // Claude 处理器引用（用于同步会话ID）
    var claudeHandler: ClaudeActionHandler? = null

    init {
        // 初始化时创建默认会话
        createDefaultSession()
    }

    /**
     * 创建默认会话
     */
    private fun createDefaultSession() {
        val defaultSession = SessionData(
            id = UUID.randomUUID().toString(),
            name = "默认会话",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            messageCount = 0
        )
        sessions[defaultSession.id] = defaultSession
        currentSessionId = defaultSession.id
        logger.info("✅ Created default session: ${defaultSession.id}")
    }

    /**
     * 处理会话操作
     */
    fun handle(request: FrontendRequest): FrontendResponse {
        return when (request.action) {
            "session.list" -> handleListSessions()
            "session.create" -> handleCreateSession(request)
            "session.switch" -> handleSwitchSession(request)
            "session.delete" -> handleDeleteSession(request)
            "session.rename" -> handleRenameSession(request)
            "session.getHistory" -> handleGetHistory(request)
            "session.saveMessage" -> handleSaveMessage(request)
            else -> FrontendResponse(false, error = "Unknown session action: ${request.action}")
        }
    }

    /**
     * 获取会话列表
     */
    private fun handleListSessions(): FrontendResponse {
        val sessionList = listSessions()
        logger.info("📋 Listing ${sessionList.size} sessions")
        return FrontendResponse(
            success = true,
            data = mapOf("sessions" to JsonArray(sessionList))
        )
    }

    /**
     * 获取会话列表（公开方法，供 RESTful API 调用）
     */
    fun listSessions(): List<JsonObject> {
        return sessions.values.map { session ->
            buildJsonObject {
                put("id", session.id)
                put("name", session.name)
                put("createdAt", session.createdAt)
                put("updatedAt", session.updatedAt)
                put("messageCount", session.messageCount)
            }
        }.sortedByDescending { it["updatedAt"]?.jsonPrimitive?.long }
    }

    /**
     * 创建新会话
     */
    private fun handleCreateSession(request: FrontendRequest): FrontendResponse {
        val data = request.data?.let { json.decodeFromJsonElement<Map<String, JsonElement>>(it) }
        val name = data?.get("name")?.jsonPrimitive?.contentOrNull ?: "新会话 ${System.currentTimeMillis()}"

        val session = createSession(name)

        return FrontendResponse(
            success = true,
            data = mapOf("session" to session)
        )
    }

    /**
     * 创建新会话（公开方法，供 RESTful API 调用）
     */
    fun createSession(name: String? = null): JsonObject {
        val sessionName = name ?: "新会话 ${System.currentTimeMillis()}"

        val newSession = SessionData(
            id = UUID.randomUUID().toString(),
            name = sessionName,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            messageCount = 0
        )

        sessions[newSession.id] = newSession
        currentSessionId = newSession.id
        // 同步到 ClaudeActionHandler
        claudeHandler?.setCurrentSessionId(newSession.id)

        logger.info("✅ Created new session: ${newSession.id} - $sessionName")

        return buildJsonObject {
            put("id", newSession.id)
            put("name", newSession.name)
            put("createdAt", newSession.createdAt)
            put("updatedAt", newSession.updatedAt)
            put("messageCount", newSession.messageCount)
        }
    }

    /**
     * 切换会话
     */
    private fun handleSwitchSession(request: FrontendRequest): FrontendResponse {
        val data = request.data?.let { json.decodeFromJsonElement<Map<String, JsonElement>>(it) }
            ?: return FrontendResponse(false, error = "Missing data")

        val sessionId = data["sessionId"]?.jsonPrimitive?.contentOrNull
            ?: return FrontendResponse(false, error = "Missing sessionId")

        if (!sessions.containsKey(sessionId)) {
            return FrontendResponse(false, error = "Session not found: $sessionId")
        }

        currentSessionId = sessionId
        // 同步到 ClaudeActionHandler
        claudeHandler?.setCurrentSessionId(sessionId)

        logger.info("🔄 Switched to session: $sessionId")

        return FrontendResponse(success = true)
    }

    /**
     * 删除会话
     */
    private fun handleDeleteSession(request: FrontendRequest): FrontendResponse {
        val data = request.data?.let { json.decodeFromJsonElement<Map<String, JsonElement>>(it) }
            ?: return FrontendResponse(false, error = "Missing data")

        val sessionId = data["sessionId"]?.jsonPrimitive?.contentOrNull
            ?: return FrontendResponse(false, error = "Missing sessionId")

        return try {
            deleteSession(sessionId)
            FrontendResponse(success = true)
        } catch (e: IllegalArgumentException) {
            FrontendResponse(false, error = e.message)
        }
    }

    /**
     * 删除会话（公开方法，供 RESTful API 调用）
     */
    fun deleteSession(sessionId: String) {
        if (!sessions.containsKey(sessionId)) {
            throw IllegalArgumentException("Session not found: $sessionId")
        }

        sessions.remove(sessionId)
        historyCache.remove(sessionId)

        // 如果删除的是当前会话,切换到第一个会话
        if (currentSessionId == sessionId) {
            currentSessionId = sessions.keys.firstOrNull()
        }

        logger.info("🗑️ Deleted session: $sessionId")
    }

    /**
     * 重命名会话
     */
    private fun handleRenameSession(request: FrontendRequest): FrontendResponse {
        val data = request.data?.let { json.decodeFromJsonElement<Map<String, JsonElement>>(it) }
            ?: return FrontendResponse(false, error = "Missing data")

        val sessionId = data["sessionId"]?.jsonPrimitive?.contentOrNull
            ?: return FrontendResponse(false, error = "Missing sessionId")
        val newName = data["name"]?.jsonPrimitive?.contentOrNull
            ?: return FrontendResponse(false, error = "Missing name")

        return try {
            renameSession(sessionId, newName)
            FrontendResponse(success = true)
        } catch (e: IllegalArgumentException) {
            FrontendResponse(false, error = e.message)
        }
    }

    /**
     * 重命名会话（公开方法，供 RESTful API 调用）
     */
    fun renameSession(sessionId: String, newName: String) {
        val session = sessions[sessionId]
            ?: throw IllegalArgumentException("Session not found: $sessionId")

        sessions[sessionId] = session.copy(
            name = newName,
            updatedAt = System.currentTimeMillis()
        )

        logger.info("✏️ Renamed session $sessionId to: $newName")
    }

    /**
     * 获取会话历史消息
     */
    private fun handleGetHistory(request: FrontendRequest): FrontendResponse {
        val data = request.data?.let { json.decodeFromJsonElement<Map<String, JsonElement>>(it) }
            ?: return FrontendResponse(false, error = "Missing data")

        val sessionId = data["sessionId"]?.jsonPrimitive?.contentOrNull
            ?: return FrontendResponse(false, error = "Missing sessionId")

        val messages = getHistory(sessionId)

        return FrontendResponse(
            success = true,
            data = mapOf("messages" to JsonArray(messages))
        )
    }

    /**
     * 获取会话历史（公开方法，供 RESTful API 调用）
     */
    fun getHistory(sessionId: String): List<JsonObject> {
        // 检查缓存
        val cachedHistory = historyCache[sessionId]
        if (cachedHistory != null) {
            logger.info("📋 Returning cached history for session: $sessionId (${cachedHistory.size} messages)")
            return cachedHistory
        }

        // TODO: 从实际存储加载历史
        // 目前返回空列表,因为消息是在内存中的
        val messages = emptyList<JsonObject>()

        // 缓存历史
        historyCache[sessionId] = messages

        logger.info("📋 Loaded history for session: $sessionId (${messages.size} messages)")

        return messages
    }

    /**
     * 处理保存消息请求（来自前端）
     */
    private fun handleSaveMessage(request: FrontendRequest): FrontendResponse {
        val data = request.data?.let { json.decodeFromJsonElement<Map<String, JsonElement>>(it) }
            ?: return FrontendResponse(false, error = "Missing data")

        val sessionId = data["sessionId"]?.jsonPrimitive?.contentOrNull
            ?: return FrontendResponse(false, error = "Missing sessionId")

        val message = data["message"]?.jsonObject
            ?: return FrontendResponse(false, error = "Missing message")

        // 保存消息
        saveMessage(sessionId, message)

        logger.info("💾 Saved message to session: $sessionId")
        return FrontendResponse(success = true)
    }

    /**
     * 保存消息到会话历史
     * 由外部调用以更新会话历史
     */
    fun saveMessage(sessionId: String, message: JsonObject) {
        val history = historyCache.getOrPut(sessionId) { mutableListOf() }.toMutableList()
        history.add(message)
        historyCache[sessionId] = history

        // 更新会话的消息计数和更新时间
        sessions[sessionId]?.let { session ->
            sessions[sessionId] = session.copy(
                messageCount = history.size,
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    /**
     * 会话数据类
     */
    private data class SessionData(
        val id: String,
        val name: String,
        val createdAt: Long,
        val updatedAt: Long,
        val messageCount: Int
    )
}
