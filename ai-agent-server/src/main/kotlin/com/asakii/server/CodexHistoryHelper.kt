package com.asakii.server

import com.asakii.codex.agent.sdk.appserver.CodexAppServerClient
import com.asakii.codex.agent.sdk.appserver.ThreadInfo
import com.asakii.logging.error
import com.asakii.logging.getLogger
import com.asakii.logging.warn
import com.asakii.rpc.api.IdeTools
import com.asakii.rpc.api.RpcHistoryMetadata
import com.asakii.rpc.api.RpcHistoryResult
import com.asakii.rpc.api.RpcHistorySession
import com.asakii.rpc.api.RpcHistorySessionsResult
import com.asakii.server.config.AiAgentServiceConfig
import com.asakii.server.history.CodexHistoryMapper
import kotlinx.coroutines.CoroutineScope
import java.nio.file.Path

private val logger = getLogger("CodexHistoryHelper")

/**
 * Codex 历史会话辅助类
 *
 * 封装 Codex 后端的历史会话查询、加载和管理功能
 */
class CodexHistoryHelper(
    private val ideTools: IdeTools,
    private val serviceConfigProvider: () -> AiAgentServiceConfig,
    private val scope: CoroutineScope
) {
    /**
     * 获取 Codex 历史会话列表
     */
    suspend fun listHistorySessions(maxResults: Int, offset: Int): RpcHistorySessionsResult {
        val config = serviceConfigProvider()
        val codexPath = config.codex.binaryPath?.takeIf { it.isNotBlank() }?.let { Path.of(it) }
        val workingDirectory = ideTools.getProjectPath().takeIf { it.isNotBlank() }?.let { Path.of(it) }

        val client = CodexAppServerClient.create(
            codexPath = codexPath,
            workingDirectory = workingDirectory,
            scope = scope
        )

        return try {
            client.initialize(
                clientName = "claude-code-plus",
                clientTitle = "Claude Code Plus",
                clientVersion = "1.0.0"
            )

            val threads = fetchThreads(client, maxResults, offset)
            val fallbackProjectPath = ideTools.getProjectPath()
            RpcHistorySessionsResult(
                sessions = threads.map { thread ->
                    // 这里不主动 resumeThread：部分环境下 thread/resume 可能阻塞，导致历史列表请求卡死。
                    // messageCount 在 UI 层允许为 -1（展示为 '—'），打开会话后再通过 metadata/load 获取真实数量即可。
                    val messageCount = -1
                    val cwd = thread.cwd.takeIf { it.isNotBlank() } ?: fallbackProjectPath
                    RpcHistorySession(
                        sessionId = thread.id,
                        firstUserMessage = thread.preview,
                        timestamp = thread.createdAt,
                        messageCount = messageCount,
                        projectPath = cwd,
                        customTitle = null
                    )
                }
            )
        } finally {
            client.close()
        }
    }

    /**
     * 获取 Codex 历史会话元数据
     */
    suspend fun getHistoryMetadata(
        threadId: String,
        fallbackProjectPath: String?
    ): RpcHistoryMetadata {
        val config = serviceConfigProvider()
        val codexPath = config.codex.binaryPath?.takeIf { it.isNotBlank() }?.let { Path.of(it) }
        val workingDirectory = (fallbackProjectPath ?: ideTools.getProjectPath())
            .takeIf { it.isNotBlank() }
            ?.let { Path.of(it) }

        val client = CodexAppServerClient.create(
            codexPath = codexPath,
            workingDirectory = workingDirectory,
            scope = scope
        )

        return try {
            client.initialize(
                clientName = "claude-code-plus",
                clientTitle = "Claude Code Plus",
                clientVersion = "1.0.0"
            )

            val thread = client.resumeThread(threadId)
            val fallback = fallbackProjectPath ?: ideTools.getProjectPath()
            CodexHistoryMapper.buildMetadata(thread, fallback)
        } finally {
            client.close()
        }
    }

    /**
     * 加载 Codex 历史会话内容
     */
    suspend fun loadHistory(
        threadId: String,
        fallbackProjectPath: String?,
        offset: Int,
        limit: Int
    ): RpcHistoryResult {
        val config = serviceConfigProvider()
        val codexPath = config.codex.binaryPath?.takeIf { it.isNotBlank() }?.let { Path.of(it) }
        val workingDirectory = (fallbackProjectPath ?: ideTools.getProjectPath())
            .takeIf { it.isNotBlank() }
            ?.let { Path.of(it) }

        val client = CodexAppServerClient.create(
            codexPath = codexPath,
            workingDirectory = workingDirectory,
            scope = scope
        )

        return try {
            client.initialize(
                clientName = "claude-code-plus",
                clientTitle = "Claude Code Plus",
                clientVersion = "1.0.0"
            )

            val thread = client.resumeThread(threadId)
            CodexHistoryMapper.buildHistoryResult(thread, offset, limit)
        } finally {
            client.close()
        }
    }

    /**
     * 归档（删除）Codex 历史会话
     */
    suspend fun archiveSession(threadId: String): Boolean {
        val config = serviceConfigProvider()
        val codexPath = config.codex.binaryPath?.takeIf { it.isNotBlank() }?.let { Path.of(it) }
        val workingDirectory = ideTools.getProjectPath().takeIf { it.isNotBlank() }?.let { Path.of(it) }

        val client = CodexAppServerClient.create(
            codexPath = codexPath,
            workingDirectory = workingDirectory,
            scope = scope
        )

        return try {
            client.initialize(
                clientName = "claude-code-plus",
                clientTitle = "Claude Code Plus",
                clientVersion = "1.0.0"
            )
            client.archiveThread(threadId)
            true
        } catch (e: Exception) {
            logger.error(e) { "❌ [HTTP] Failed to archive Codex thread: $threadId" }
            false
        } finally {
            client.close()
        }
    }

    /**
     * 分页获取 Codex 线程列表
     */
    private suspend fun fetchThreads(
        client: CodexAppServerClient,
        maxResults: Int,
        offset: Int
    ): List<ThreadInfo> {
        val result = mutableListOf<ThreadInfo>()
        var cursor: String? = null
        var skipped = 0

        while (result.size < maxResults) {
            val limit = (maxResults + offset - skipped).coerceAtLeast(1)
            val page = client.listThreads(cursor = cursor, limit = limit)
            if (page.data.isEmpty()) break

            for (thread in page.data) {
                if (skipped < offset) {
                    skipped += 1
                    continue
                }
                result.add(thread)
                if (result.size >= maxResults) break
            }

            cursor = page.nextCursor?.takeIf { it.isNotBlank() }
            if (cursor == null) break
        }

        return result
    }
}
