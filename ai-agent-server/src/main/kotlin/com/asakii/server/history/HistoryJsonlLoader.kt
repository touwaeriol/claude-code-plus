package com.asakii.server.history

import com.asakii.rpc.api.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import com.asakii.claude.agent.sdk.utils.ClaudeSessionScanner
import com.asakii.claude.agent.sdk.utils.ProjectPathUtils
import com.asakii.ai.agent.sdk.model.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.useLines

/**
 * 将 JSONL 会话文件转换为 RpcMessage 流（强类型）。
 *
 * 核心算法（复刻官方 Claude CLI）：
 * 1. 使用 parentUuid 构建消息树结构
 * 2. 自动选择最新分支（时间戳最新的叶节点）
 * 3. 从叶节点回溯到根节点，重建线性对话历史
 *
 * 性能优化：
 * 1. 文件元数据缓存 - 避免重复扫描文件获取行数
 * 2. 从尾部高效加载 - 使用 RandomAccessFile 从尾部向前读取
 */
object HistoryJsonlLoader {
    private val log = KotlinLogging.logger {}
    private val parser = Json { ignoreUnknownKeys = true }

    // ========== 消息树数据结构（复刻官方 CLI） ==========

    /**
     * JSONL 条目，包含消息树相关字段
     * 用于构建消息树并选择正确的分支
     */
    private data class JsonlEntry(
        val uuid: String,
        val parentUuid: String?,
        val type: String,
        val timestamp: String?,
        val json: JsonObject  // 原始 JSON，用于后续转换
    )

    // ========== 消息树算法（复刻官方 CLI） ==========

    /**
     * 解析 JSONL 文件，构建消息树
     * 只收集实际的消息（user, assistant），忽略系统消息
     *
     * @param file JSONL 历史文件
     * @return uuid -> JsonlEntry 的映射
     */
    private fun parseMessageTree(file: File): Map<String, JsonlEntry> {
        val messages = mutableMapOf<String, JsonlEntry>()

        file.bufferedReader().use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) continue

                try {
                    val json = parser.parseToJsonElement(line).jsonObject
                    val type = json["type"]?.jsonPrimitive?.contentOrNull ?: continue

                    // 只收集实际的消息（user, assistant）
                    if (type == "user" || type == "assistant") {
                        val uuid = json["uuid"]?.jsonPrimitive?.contentOrNull ?: continue
                        val parentUuid = json["parentUuid"]?.jsonPrimitive?.contentOrNull
                        val timestamp = json["timestamp"]?.jsonPrimitive?.contentOrNull

                        messages[uuid] = JsonlEntry(uuid, parentUuid, type, timestamp, json)
                    }
                } catch (e: Exception) {
                    log.debug("[History] 解析消息树行失败: ${e.message}")
                }
            }
        }

        log.debug("[History] 消息树构建完成: ${messages.size} 条消息")
        return messages
    }

    /**
     * 找到所有叶节点（没有子节点的消息）
     * 叶节点 = 没有被任何消息作为 parentUuid 引用的消息
     *
     * @param messages uuid -> JsonlEntry 的映射
     * @return 叶节点列表
     */
    private fun findLeafNodes(messages: Map<String, JsonlEntry>): List<JsonlEntry> {
        // 收集所有被引用为 parentUuid 的 uuid
        val referencedAsParent = messages.values
            .mapNotNull { it.parentUuid }
            .toSet()

        // 叶节点 = uuid 不在 referencedAsParent 中的消息
        val leafNodes = messages.values.filter { it.uuid !in referencedAsParent }
        log.debug("[History] 找到 ${leafNodes.size} 个叶节点")
        return leafNodes
    }

    /**
     * 选择时间戳最新的叶节点
     * 如果有多个分支，选择最新更新的那个
     *
     * @param leafNodes 叶节点列表
     * @return 最新的叶节点，如果列表为空返回 null
     */
    private fun selectLatestLeaf(leafNodes: List<JsonlEntry>): JsonlEntry? {
        if (leafNodes.isEmpty()) return null

        return leafNodes.maxByOrNull { entry ->
            entry.timestamp?.let {
                try {
                    java.time.Instant.parse(it).toEpochMilli()
                } catch (e: Exception) {
                    0L
                }
            } ?: 0L
        }
    }

    /**
     * 从叶节点回溯到根节点，构建线性路径
     * 这是官方 CLI 的核心算法，通过 parentUuid 链重建对话历史
     *
     * @param messages uuid -> JsonlEntry 的映射
     * @param leaf 叶节点（最新的消息）
     * @return 从根到叶的线性路径（按时间顺序，最早的在前）
     */
    private fun buildPathFromLeaf(
        messages: Map<String, JsonlEntry>,
        leaf: JsonlEntry
    ): List<JsonlEntry> {
        val path = mutableListOf<JsonlEntry>()
        var current: JsonlEntry? = leaf

        while (current != null) {
            path.add(0, current)  // 头部插入，保证从根到叶的顺序
            current = current.parentUuid?.let { messages[it] }
        }

        log.debug("[History] 构建路径完成: ${path.size} 条消息")
        return path
    }

    /**
     * 使用消息树算法加载历史消息（复刻官方 CLI）
     *
     * 算法流程（与 CLI 的 Nm 函数一致）：
     * 1. 解析 JSONL 文件，构建 uuid -> message 的 Map
     * 2. 如果有 leafUuid，使用它定位分支
     * 3. 否则找到所有叶节点，选择时间戳最新的叶节点
     * 4. 从叶节点回溯到根节点，重建线性对话历史
     *
     * @param file JSONL 历史文件
     * @param leafUuid 可选的叶节点 UUID，用于恢复到特定分支
     * @return 线性对话历史（只包含指定分支或最新分支）
     */
    private fun loadWithMessageTree(file: File, leafUuid: String? = null): List<UiStreamEvent> {
        // Step 1: 构建消息树
        val messages = parseMessageTree(file)
        if (messages.isEmpty()) {
            log.debug("[History] 消息树为空")
            return emptyList()
        }

        // Step 2: 如果有 leafUuid，尝试使用它定位分支（与 CLI 一致）
        val targetLeaf: JsonlEntry? = if (!leafUuid.isNullOrBlank()) {
            val found = messages[leafUuid]
            if (found != null) {
                log.info("[History] 使用指定的 leafUuid: $leafUuid")
                found
            } else {
                log.warn("[History] 指定的 leafUuid 不存在: $leafUuid，回退到自动选择")
                null
            }
        } else {
            null
        }

        // Step 3: 如果没有指定 leafUuid 或找不到，自动选择最新分支
        val selectedLeaf = targetLeaf ?: run {
            val leafNodes = findLeafNodes(messages)
            if (leafNodes.isEmpty()) {
                log.warn("[History] 未找到叶节点，回退到线性读取")
                return loadLinear(file)
            }

            val latestLeaf = selectLatestLeaf(leafNodes)
            if (latestLeaf == null) {
                log.warn("[History] 无法选择最新叶节点，回退到线性读取")
                return loadLinear(file)
            }
            log.info("[History] 自动选择最新分支: uuid=${latestLeaf.uuid}, timestamp=${latestLeaf.timestamp}")
            latestLeaf
        }

        // Step 4: 回溯构建线性路径
        val path = buildPathFromLeaf(messages, selectedLeaf)

        // 转换为 UiStreamEvent
        return path.mapNotNull { entry ->
            toUiStreamEvent(entry.json)
        }
    }

    /**
     * 线性读取历史消息（回退方案）
     * 用于早期没有 uuid 的历史文件
     */
    private fun loadLinear(file: File): List<UiStreamEvent> {
        val result = mutableListOf<UiStreamEvent>()

        file.bufferedReader().use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) continue

                try {
                    val obj = parser.parseToJsonElement(line).jsonObject
                    val messageType = obj["type"]?.jsonPrimitive?.contentOrNull

                    if (!shouldDisplay(messageType)) continue

                    val uiEvent = toUiStreamEvent(obj)
                    if (uiEvent != null) {
                        result.add(uiEvent)
                    }
                } catch (e: Exception) {
                    log.debug("[History] 线性读取解析行失败: ${e.message}")
                }
            }
        }

        return result
    }

    /**
     * 文件元数据缓存
     * key: 文件绝对路径
     * value: CachedFileMetadata
     */
    private data class CachedFileMetadata(
        val lastModified: Long,
        val fileSize: Long,
        val totalLines: Int,
        val displayableLines: Int  // 可显示的消息行数（过滤后）
    )

    private val metadataCache = ConcurrentHashMap<String, CachedFileMetadata>()

    /**
     * 获取或刷新文件元数据（带缓存）
     * 只有当文件被修改时才重新扫描
     */
    private fun getOrRefreshMetadata(historyFile: File): CachedFileMetadata? {
        if (!historyFile.exists()) return null

        val filePath = historyFile.absolutePath
        val currentLastModified = historyFile.lastModified()
        val currentFileSize = historyFile.length()

        // 检查缓存是否有效
        val cached = metadataCache[filePath]
        if (cached != null &&
            cached.lastModified == currentLastModified &&
            cached.fileSize == currentFileSize
        ) {
            log.debug("[History] 使用缓存的元数据: $filePath (totalLines=${cached.totalLines}, displayable=${cached.displayableLines})")
            return cached
        }

        // 缓存失效或不存在，重新扫描
        log.info("[History] 扫描文件元数据: $filePath")
        var totalLines = 0
        var displayableLines = 0

        historyFile.bufferedReader().use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) continue
                totalLines++

                // 快速检查是否为可显示类型（避免完整 JSON 解析）
                if (line.contains("\"type\"")) {
                    when {
                        line.contains("\"user\"") ||
                        line.contains("\"assistant\"") ||
                        line.contains("\"summary\"") -> displayableLines++
                    }
                }
            }
        }

        val metadata = CachedFileMetadata(
            lastModified = currentLastModified,
            fileSize = currentFileSize,
            totalLines = totalLines,
            displayableLines = displayableLines
        )
        metadataCache[filePath] = metadata
        log.info("[History] 元数据已缓存: totalLines=$totalLines, displayable=$displayableLines")
        return metadata
    }

    /**
     * 统计历史文件的总行数（物理行数，包含所有类型消息）
     * 使用缓存优化，避免重复扫描
     * @return 文件总行数，文件不存在返回 0
     */
    fun countLines(sessionId: String?, projectPath: String?): Int {
        if (sessionId.isNullOrBlank() || projectPath.isNullOrBlank()) {
            return 0
        }

        val claudeDir = ClaudeSessionScanner.getClaudeDir()
        val projectId = ProjectPathUtils.projectPathToDirectoryName(projectPath)
        val historyFile = File(claudeDir, "projects/$projectId/$sessionId.jsonl")

        return getOrRefreshMetadata(historyFile)?.totalLines ?: 0
    }

    /**
     * 获取可显示的消息行数（过滤后）
     * 使用缓存优化
     */
    fun countDisplayableLines(sessionId: String?, projectPath: String?): Int {
        if (sessionId.isNullOrBlank() || projectPath.isNullOrBlank()) {
            return 0
        }

        val claudeDir = ClaudeSessionScanner.getClaudeDir()
        val projectId = ProjectPathUtils.projectPathToDirectoryName(projectPath)
        val historyFile = File(claudeDir, "projects/$projectId/$sessionId.jsonl")

        return getOrRefreshMetadata(historyFile)?.displayableLines ?: 0
    }

    /**
     * 从文件尾部高效查找最新的 custom-title 记录
     *
     * 算法：使用 RandomAccessFile 从文件尾部向前逐行读取，
     * 找到第一个 type="custom-title" 的记录即返回（最新的）
     *
     * @param sessionId 会话 ID
     * @param projectPath 项目路径
     * @return customTitle 值，如果不存在则返回 null
     */
    fun findCustomTitle(sessionId: String?, projectPath: String?): String? {
        if (sessionId.isNullOrBlank() || projectPath.isNullOrBlank()) {
            return null
        }

        val claudeDir = ClaudeSessionScanner.getClaudeDir()
        val projectId = ProjectPathUtils.projectPathToDirectoryName(projectPath)
        val historyFile = File(claudeDir, "projects/$projectId/$sessionId.jsonl")

        if (!historyFile.exists()) {
            log.debug("[History] 查找 custom-title 失败: 文件不存在 ${historyFile.absolutePath}")
            return null
        }

        return try {
            findCustomTitleFromTail(historyFile)
        } catch (e: Exception) {
            log.warn("[History] 查找 custom-title 失败: ${e.message}")
            null
        }
    }

    /**
     * 从文件尾部向前搜索 custom-title 记录
     * 使用 RandomAccessFile 实现高效的尾部读取
     */
    private fun findCustomTitleFromTail(file: File): String? {
        val fileLength = file.length()
        if (fileLength == 0L) return null

        RandomAccessFile(file, "r").use { raf ->
            // 从文件末尾开始，逐块向前读取
            val bufferSize = 4096  // 4KB 缓冲区
            var position = fileLength
            val lineBuffer = StringBuilder()

            while (position > 0) {
                // 计算本次读取的起始位置和长度
                val readStart = maxOf(0L, position - bufferSize)
                val readLength = (position - readStart).toInt()

                // 定位并读取
                raf.seek(readStart)
                val buffer = ByteArray(readLength)
                raf.readFully(buffer)

                // 将读取的内容与之前的行缓冲区合并（注意顺序）
                val chunk = String(buffer, Charsets.UTF_8)
                lineBuffer.insert(0, chunk)

                // 按行分割并检查
                val lines = lineBuffer.toString().split('\n')

                // 除了第一个片段（可能不完整），检查其他行
                for (i in lines.size - 1 downTo 1) {
                    val line = lines[i].trim()
                    if (line.isBlank()) continue

                    val customTitle = tryParseCustomTitle(line)
                    if (customTitle != null) {
                        log.info("[History] 找到 custom-title: $customTitle")
                        return customTitle
                    }
                }

                // 保留第一个片段（可能是不完整的行）
                lineBuffer.clear()
                lineBuffer.append(lines[0])

                position = readStart
            }

            // 处理最后剩余的内容（文件开头的部分行）
            val remainingLine = lineBuffer.toString().trim()
            if (remainingLine.isNotBlank()) {
                val customTitle = tryParseCustomTitle(remainingLine)
                if (customTitle != null) {
                    log.info("[History] 找到 custom-title (文件开头): $customTitle")
                    return customTitle
                }
            }
        }

        log.debug("[History] 未找到 custom-title 记录")
        return null
    }

    /**
     * 尝试解析行内容为 custom-title
     * @return customTitle 值，如果不是 custom-title 类型则返回 null
     */
    private fun tryParseCustomTitle(line: String): String? {
        if (!line.contains("\"type\"") || !line.contains("\"custom-title\"")) {
            return null  // 快速过滤，避免不必要的 JSON 解析
        }

        return try {
            val json = parser.parseToJsonElement(line).jsonObject
            val type = json["type"]?.jsonPrimitive?.contentOrNull
            if (type == "custom-title") {
                json["customTitle"]?.jsonPrimitive?.contentOrNull
            } else {
                null
            }
        } catch (e: Exception) {
            null  // 解析失败，不是有效的 JSON
        }
    }

    /**
     * 加载历史消息（使用消息树算法，复刻官方 CLI 的 Nm 函数）
     *
     * 核心算法：
     * 1. 使用 parentUuid 构建消息树
     * 2. 如果提供了 leafUuid，使用它定位到特定分支
     * 3. 否则找到叶节点，选择时间戳最新的分支
     * 4. 从叶节点回溯到根节点，重建线性对话历史
     *
     * 这确保了当用户编辑重发消息时，只返回最新分支的消息，而不是所有分支。
     *
     * @param sessionId 目标会话 ID（必填）
     * @param projectPath 项目路径（必填）
     * @param offset 跳过条数（目前仅在 offset < 0 && limit > 0 时使用尾部加载）
     * @param limit 限制条数（<=0 表示全部）
     * @param leafUuid 可选的叶节点 UUID，用于恢复到特定分支（与 CLI 的 Nm 函数一致）
     */
    fun loadHistoryMessages(
        sessionId: String?,
        projectPath: String?,
        offset: Int = 0,
        limit: Int = 0,
        leafUuid: String? = null
    ): List<UiStreamEvent> {
        if (sessionId.isNullOrBlank() || projectPath.isNullOrBlank()) {
            log.warn("[History] sessionId/projectPath 缺失，跳过加载")
            return emptyList()
        }

        val claudeDir = ClaudeSessionScanner.getClaudeDir()
        val projectId = ProjectPathUtils.projectPathToDirectoryName(projectPath)
        val historyFile = File(claudeDir, "projects/$projectId/$sessionId.jsonl")
        if (!historyFile.exists()) {
            log.warn("[History] 文件不存在: ${historyFile.absolutePath}")
            return emptyList()
        }

        log.info("[History] 加载 JSONL: session=$sessionId file=${historyFile.absolutePath} offset=$offset limit=$limit leafUuid=$leafUuid")

        val startTime = System.currentTimeMillis()

        // 🆕 使用消息树算法（复刻官方 CLI 的 Nm 函数）
        val result = loadWithMessageTree(historyFile, leafUuid)

        val elapsed = System.currentTimeMillis() - startTime
        log.info("[History] 完成（消息树算法）: loaded=${result.size} 耗时=${elapsed}ms")

        // 应用 offset 和 limit
        val finalResult = if (offset < 0 && limit > 0) {
            // 从尾部取 limit 条
            result.takeLast(limit)
        } else {
            result.drop(offset.coerceAtLeast(0))
                .let { if (limit > 0) it.take(limit) else it }
        }

        return finalResult
    }

    /**
     * 从文件尾部高效加载消息
     * 使用 RandomAccessFile 从尾部向前读取，只解析需要的消息数量
     *
     * 算法：
     * 1. 从文件末尾向前读取字节块
     * 2. 按行分割，从后向前解析 JSON
     * 3. 过滤出可显示的消息
     * 4. 收集到足够数量后停止
     *
     * @param file 历史文件
     * @param limit 需要加载的消息数量
     * @return 从旧到新排序的消息列表
     */
    private fun loadFromTailEfficient(file: File, limit: Int): List<UiStreamEvent> {
        val fileLength = file.length()
        if (fileLength == 0L) return emptyList()

        val result = mutableListOf<UiStreamEvent>()

        RandomAccessFile(file, "r").use { raf ->
            val bufferSize = 32 * 1024  // 32KB 缓冲区（JSONL 行可能很长）
            var position = fileLength
            val lineBuffer = StringBuilder()
            var linesRead = 0

            while (position > 0 && result.size < limit) {
                // 计算本次读取的起始位置和长度
                val readStart = maxOf(0L, position - bufferSize)
                val readLength = (position - readStart).toInt()

                // 定位并读取
                raf.seek(readStart)
                val buffer = ByteArray(readLength)
                raf.readFully(buffer)

                // 将读取的内容与之前的行缓冲区合并（注意顺序）
                val chunk = String(buffer, Charsets.UTF_8)
                lineBuffer.insert(0, chunk)

                // 按行分割并检查
                val lines = lineBuffer.toString().split('\n')

                // 除了第一个片段（可能不完整），从后向前检查其他行
                for (i in lines.size - 1 downTo 1) {
                    if (result.size >= limit) break

                    val line = lines[i].trim()
                    if (line.isBlank()) continue
                    linesRead++

                    try {
                        val obj = parser.parseToJsonElement(line).jsonObject
                        val messageType = obj["type"]?.jsonPrimitive?.contentOrNull

                        if (!shouldDisplay(messageType)) continue

                        val uiEvent = toUiStreamEvent(obj)
                        if (uiEvent != null) {
                            result.add(0, uiEvent)  // 添加到头部，保持从旧到新的顺序
                        }
                    } catch (e: Exception) {
                        log.debug("[History] 尾部加载解析行失败: ${e.message}")
                    }
                }

                // 保留第一个片段（可能是不完整的行）
                lineBuffer.clear()
                lineBuffer.append(lines[0])

                position = readStart
            }

            // 处理最后剩余的内容（文件开头的部分行）
            if (result.size < limit) {
                val remainingLine = lineBuffer.toString().trim()
                if (remainingLine.isNotBlank()) {
                    try {
                        val obj = parser.parseToJsonElement(remainingLine).jsonObject
                        val messageType = obj["type"]?.jsonPrimitive?.contentOrNull
                        if (shouldDisplay(messageType)) {
                            val uiEvent = toUiStreamEvent(obj)
                            if (uiEvent != null) {
                                result.add(0, uiEvent)
                            }
                        }
                    } catch (e: Exception) {
                        log.debug("[History] 尾部加载解析首行失败: ${e.message}")
                    }
                }
            }

            log.debug("[History] 尾部加载扫描了 $linesRead 行")
        }

        return result
    }

    /**
     * 判断消息类型是否应该显示给前端
     * 过滤掉系统消息、压缩边界等不需要显示的类型
     */
    private fun shouldDisplay(type: String?): Boolean {
        return when (type) {
            "user", "assistant", "summary" -> true
            "compact_boundary", "status", "file-history-snapshot" -> false
            else -> false  // 未知类型默认不显示
        }
    }

    private fun toUiStreamEvent(json: JsonObject): UiStreamEvent? {
        val type = json["type"]?.jsonPrimitive?.contentOrNull ?: return null

        return when (type) {
            "user" -> buildUserMessage(json)
            "assistant" -> buildAssistantMessage(json)
            "summary" -> buildResultMessage(json)
            else -> null  // 其他类型已经被 shouldDisplay() 过滤，不应该到这里
        }
    }

    private fun buildUserMessage(json: JsonObject): UiStreamEvent? {
        val contentBlocks = extractContentBlocks(json) ?: return null
        return UiUserMessage(
            content = contentBlocks,
            isReplay = null
        )
    }

    private fun buildAssistantMessage(json: JsonObject): UiStreamEvent? {
        val contentBlocks = extractContentBlocks(json) ?: return null
        val messageObj = json["message"]?.jsonObject
        val id = messageObj?.get("id")?.jsonPrimitive?.contentOrNull
        // 解析 parent_tool_use_id（用于子代理消息路由）
        val parentToolUseId = json["parent_tool_use_id"]?.jsonPrimitive?.contentOrNull
        return UiAssistantMessage(
            id = id,
            content = contentBlocks,
            parentToolUseId = parentToolUseId
        )
    }

    private fun buildResultMessage(json: JsonObject): UiStreamEvent? {
        val summary = json["summary"]?.jsonPrimitive?.contentOrNull ?: return null
        // summary 消息暂时不转换，历史加载不需要显示
        return null
    }

    private fun extractContentBlocks(json: JsonObject): List<UnifiedContentBlock>? {
        val messageObj = json["message"]?.jsonObject ?: return null
        val contentElement = messageObj["content"] ?: return null

        return when (contentElement) {
            is kotlinx.serialization.json.JsonPrimitive -> if (contentElement.isString) {
                listOf(TextContent(contentElement.content))
            } else emptyList()
            is JsonArray -> {
                // 即使部分 block 解析失败，也保留成功解析的 blocks
                val parsed = contentElement.mapNotNull { item -> parseContentBlock(item) }
                if (parsed.isEmpty()) {
                    // 如果所有 blocks 都解析失败，记录警告但仍返回空列表而不是 null
                    log.warn("[History] 所有 content blocks 解析失败，返回空列表: ${contentElement.toString().take(100)}")
                }
                parsed
            }
            else -> emptyList()
        }
    }

    private fun parseContentBlock(item: JsonElement): UnifiedContentBlock? {
        if (item !is JsonObject) return null
        return when (item["type"]?.jsonPrimitive?.contentOrNull) {
            "text" -> item["text"]?.jsonPrimitive?.contentOrNull?.let { TextContent(it) }

            "thinking" -> item["thinking"]?.jsonPrimitive?.contentOrNull?.let {
                ThinkingContent(it, signature = null)
            }

            "tool_use" -> {
                val id = item["id"]?.jsonPrimitive?.contentOrNull ?: ""
                val name = item["name"]?.jsonPrimitive?.contentOrNull ?: ""
                val inputJson = item["input"]  // 保持原始 JsonElement
                ToolUseContent(
                    id = id,
                    name = name,
                    input = inputJson,
                    status = ContentStatus.IN_PROGRESS
                )
            }

            "tool_result" -> {
                val toolUseId = item["tool_use_id"]?.jsonPrimitive?.contentOrNull ?: ""
                val isError = item["is_error"]?.jsonPrimitive?.booleanOrNull ?: false
                val contentJson = item["content"]
                val agentId = extractAgentIdFromContent(contentJson)
                log.info("[History] tool_result: toolUseId=$toolUseId, agentId=$agentId, contentPreview=${contentJson?.toString()?.take(200)}")
                ToolResultContent(
                    toolUseId = toolUseId,
                    content = contentJson,
                    isError = isError,
                    agentId = agentId
                )
            }

            else -> null  // 忽略未知类型
        }
    }

    /**
     * 从 tool_result 的 content 中提取 agentId（仅 Task 工具有）
     * 匹配模式: "agentId: xxx" 或 "agentId: xxx (..."
     */
    private fun extractAgentIdFromContent(content: JsonElement?): String? {
        if (content == null) return null

        // 如果是字符串，直接解析
        if (content is kotlinx.serialization.json.JsonPrimitive && content.isString) {
            return parseAgentIdFromText(content.content)
        }

        // 如果是数组，查找 text 块
        if (content is JsonArray) {
            for (item in content) {
                if (item !is JsonObject) continue
                if (item["type"]?.jsonPrimitive?.contentOrNull == "text") {
                    val text = item["text"]?.jsonPrimitive?.contentOrNull ?: continue
                    parseAgentIdFromText(text)?.let { return it }
                }
            }
        }

        return null
    }

    private fun parseAgentIdFromText(text: String): String? {
        val regex = Regex("""agentId:\s*([a-f0-9]+)""", RegexOption.IGNORE_CASE)
        return regex.find(text)?.groupValues?.get(1)
    }

    /**
     * Truncate history file from the message with the specified UUID.
     * Keeps all messages before the target UUID, removes the target message and all subsequent messages.
     *
     * Implementation:
     * 1. Read lines until we find the target UUID
     * 2. Write all lines before the target to a temporary file
     * 3. Replace original file with temp file
     * 4. Clear metadata cache
     *
     * @param sessionId Session ID
     * @param projectPath Project path
     * @param messageUuid UUID of the message to truncate from (inclusive - this message will be removed)
     * @return Number of physical lines remaining after truncation
     * @throws IllegalArgumentException if sessionId, projectPath or messageUuid is blank
     * @throws IllegalStateException if file does not exist or UUID not found
     */
    fun truncateHistory(
        sessionId: String,
        projectPath: String,
        messageUuid: String
    ): Int {
        require(sessionId.isNotBlank()) { "sessionId cannot be blank" }
        require(projectPath.isNotBlank()) { "projectPath cannot be blank" }
        require(messageUuid.isNotBlank()) { "messageUuid cannot be blank" }

        val claudeDir = ClaudeSessionScanner.getClaudeDir()
        val projectId = ProjectPathUtils.projectPathToDirectoryName(projectPath)
        val historyFile = File(claudeDir, "projects/$projectId/$sessionId.jsonl")

        if (!historyFile.exists()) {
            log.warn("[History] Truncate failed: file does not exist ${historyFile.absolutePath}")
            throw IllegalStateException("History file does not exist: ${historyFile.absolutePath}")
        }

        log.info("[History] Truncating history: sessionId=$sessionId, messageUuid=$messageUuid, file=${historyFile.absolutePath}")

        // Create temp file
        val tempFile = File(historyFile.parentFile, "${sessionId}.jsonl.tmp")

        try {
            var keptLines = 0
            var foundUuid = false

            historyFile.bufferedReader().use { reader ->
                tempFile.bufferedWriter().use { writer ->
                    while (true) {
                        val line = reader.readLine() ?: break

                        // Check if this line contains the target UUID
                        // Quick string check first, then parse JSON if needed
                        if (line.contains("\"uuid\"") && line.contains(messageUuid)) {
                            // Verify by parsing JSON
                            try {
                                val json = parser.parseToJsonElement(line).jsonObject
                                val uuid = json["uuid"]?.jsonPrimitive?.contentOrNull
                                if (uuid == messageUuid) {
                                    foundUuid = true
                                    log.info("[History] Found target UUID at line ${keptLines + 1}, truncating from here")
                                    break  // Stop writing, truncate from this point
                                }
                            } catch (e: Exception) {
                                // JSON parse failed, continue
                            }
                        }

                        // Write line to temp file
                        writer.write(line)
                        writer.newLine()
                        keptLines++
                    }
                }
            }

            if (!foundUuid) {
                // Clean up temp file
                tempFile.delete()
                log.warn("[History] Truncate failed: UUID not found $messageUuid")
                throw IllegalStateException("Message UUID not found in history: $messageUuid")
            }

            // Replace original file with temp file
            // On Windows, rename might fail if the file is locked, so use copy + delete
            if (!tempFile.renameTo(historyFile)) {
                log.debug("[History] rename failed, using copy + delete")
                tempFile.copyTo(historyFile, overwrite = true)
                tempFile.delete()
            }

            // Clear cached metadata for this file
            metadataCache.remove(historyFile.absolutePath)

            log.info("[History] Truncation complete: kept $keptLines lines")
            return keptLines
        } catch (e: Exception) {
            // Clean up temp file on error
            if (tempFile.exists()) {
                tempFile.delete()
            }
            if (e is IllegalStateException) throw e  // Re-throw our own exceptions
            log.error("[History] Truncation failed: ${e.message}", e)
            throw e
        }
    }
}
