package com.claudecodeplus.ui.services

import com.claudecodeplus.ui.models.*
import com.claudecodeplus.ui.services.UnifiedSessionService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.Instant
import java.time.Duration
import java.util.UUID

/**
 * 对话历史智能总结服务
 */
class ChatSummaryService(
    private val unifiedSessionService: UnifiedSessionService
) {
    private val summaryCache = mutableMapOf<String, ChatSummary>()
    
    /**
     * 生成对话总结
     */
    suspend fun generateSummary(
        messages: List<EnhancedMessage>,
        summaryType: SummaryType = SummaryType.CONCISE,
        options: SummaryOptions = SummaryOptions()
    ): ChatSummary = withContext(Dispatchers.IO) {
        // 检查缓存
        val cacheKey = generateCacheKey(messages, summaryType, options)
        summaryCache[cacheKey]?.let { return@withContext it }
        
        // 准备对话内容
        val conversation = prepareConversation(messages, options)
        
        // 生成总结提示词
        val prompt = buildSummaryPrompt(conversation, summaryType, options)
        
        // 调用 Claude 生成总结
        val summaryText = callClaude(prompt)
        
        // 解析总结结果
        val summary = parseSummary(summaryText, messages, summaryType)
        
        // 缓存结果
        summaryCache[cacheKey] = summary
        
        summary
    }
    
    /**
     * 生成对话段落总结
     */
    suspend fun generateSegmentedSummary(
        messages: List<EnhancedMessage>,
        segmentSize: Int = 10
    ): SegmentedSummary = withContext(Dispatchers.IO) {
        val segments = messages.chunked(segmentSize)
        val segmentSummaries = segments.mapIndexed { index, segment ->
            val summary = generateSummary(
                segment,
                SummaryType.DETAILED,
                SummaryOptions(includeKeyPoints = true)
            )
            
            SegmentSummary(
                segmentIndex = index,
                startMessageId = segment.first().id,
                endMessageId = segment.last().id,
                summary = summary.summary,
                keyPoints = summary.keyPoints ?: emptyList(),
                timestamp = Instant.ofEpochMilli(segment.last().timestamp)
            )
        }
        
        SegmentedSummary(
            totalSegments = segments.size,
            segments = segmentSummaries,
            overallSummary = generateOverallSummary(segmentSummaries)
        )
    }
    
    /**
     * 生成进度总结
     */
    suspend fun generateProgressSummary(
        messages: List<EnhancedMessage>,
        contextItems: List<ContextItem> = emptyList()
    ): ProgressSummary = withContext(Dispatchers.IO) {
        val prompt = buildProgressSummaryPrompt(messages, contextItems)
        val response = callClaude(prompt)
        
        parseProgressSummary(response, messages)
    }
    
    /**
     * 生成技术总结
     */
    suspend fun generateTechnicalSummary(
        messages: List<EnhancedMessage>
    ): TechnicalSummary = withContext(Dispatchers.IO) {
        val prompt = buildTechnicalSummaryPrompt(messages)
        val response = callClaude(prompt)
        
        parseTechnicalSummary(response, messages)
    }
    
    /**
     * 生成行动项总结
     */
    suspend fun extractActionItems(
        messages: List<EnhancedMessage>
    ): List<ActionItem> = withContext(Dispatchers.IO) {
        val prompt = buildActionItemsPrompt(messages)
        val response = callClaude(prompt)
        
        parseActionItems(response)
    }
    
    /**
     * 生成决策点总结
     */
    suspend fun extractDecisionPoints(
        messages: List<EnhancedMessage>
    ): List<DecisionPoint> = withContext(Dispatchers.IO) {
        val prompt = buildDecisionPointsPrompt(messages)
        val response = callClaude(prompt)
        
        parseDecisionPoints(response)
    }
    
    /**
     * 自动总结长对话
     */
    fun shouldAutoSummarize(messages: List<EnhancedMessage>): Boolean {
        return messages.size > 20 || 
               messages.sumOf { it.content.length } > 10000
    }
    
    /**
     * 批量生成总结
     */
    suspend fun batchGenerateSummaries(
        chatTabs: List<ChatTab>,
        summaryType: SummaryType = SummaryType.CONCISE
    ): Map<String, ChatSummary> = withContext(Dispatchers.IO) {
        chatTabs.map { tab ->
            async {
                tab.id to generateSummary(tab.messages, summaryType)
            }
        }.awaitAll().toMap()
    }
    
    // 私有辅助方法
    
    private fun prepareConversation(
        messages: List<EnhancedMessage>,
        options: SummaryOptions
    ): String {
        return buildString {
            messages.forEach { message ->
                appendLine("${if (message.role == MessageRole.USER) "用户" else "助手"}: ${message.content}")
                
                if (options.includeTimestamps) {
                    appendLine("时间: ${message.timestamp}")
                }
                
                appendLine()
            }
        }
    }
    
    private fun buildSummaryPrompt(
        conversation: String,
        summaryType: SummaryType,
        options: SummaryOptions
    ): String {
        return buildString {
            appendLine("请为以下对话生成${summaryType.description}总结：")
            appendLine()
            appendLine(conversation)
            appendLine()
            
            when (summaryType) {
                SummaryType.CONCISE -> {
                    appendLine("要求：")
                    appendLine("1. 用1-2句话概括对话的主要内容")
                    appendLine("2. 突出最重要的结论或成果")
                }
                
                SummaryType.DETAILED -> {
                    appendLine("要求：")
                    appendLine("1. 详细总结对话的主要内容")
                    appendLine("2. 列出关键讨论点")
                    appendLine("3. 总结达成的结论")
                }
                
                SummaryType.TECHNICAL -> {
                    appendLine("要求：")
                    appendLine("1. 重点关注技术细节")
                    appendLine("2. 列出使用的技术栈")
                    appendLine("3. 总结技术方案和实现")
                }
                
                SummaryType.EXECUTIVE -> {
                    appendLine("要求：")
                    appendLine("1. 高层次概括")
                    appendLine("2. 突出业务价值")
                    appendLine("3. 列出关键决策和结果")
                }
            }
            
            if (options.includeKeyPoints) {
                appendLine("4. 列出3-5个关键要点")
            }
            
            if (options.includeNextSteps) {
                appendLine("5. 建议下一步行动")
            }
            
            if (options.includeQuestions) {
                appendLine("6. 列出待解决的问题")
            }
        }
    }
    
    private fun buildProgressSummaryPrompt(
        messages: List<EnhancedMessage>,
        contextItems: List<ContextItem>
    ): String {
        return buildString {
            appendLine("请分析以下对话，生成进度总结：")
            appendLine()
            
            if (contextItems.isNotEmpty()) {
                appendLine("涉及的文件/资源：")
                contextItems.forEach { item ->
                    when (item) {
                        is ContextItem.File -> appendLine("- 文件: ${item.path}")
                        is ContextItem.Folder -> appendLine("- 文件夹: ${item.path}")
                        is ContextItem.CodeBlock -> appendLine("- 代码块: ${item.language}")
                    }
                }
                appendLine()
            }
            
            appendLine("对话内容：")
            messages.forEach { message ->
                appendLine("${if (message.role == MessageRole.USER) "用户" else "助手"}: ${message.content.take(200)}...")
            }
            
            appendLine()
            appendLine("请提供：")
            appendLine("1. 完成的任务列表")
            appendLine("2. 正在进行的任务")
            appendLine("3. 待处理的任务")
            appendLine("4. 遇到的问题和解决状态")
            appendLine("5. 整体进度百分比估计")
        }
    }
    
    private fun buildTechnicalSummaryPrompt(messages: List<EnhancedMessage>): String {
        return buildString {
            appendLine("请生成技术总结，分析以下对话中的技术内容：")
            appendLine()
            
            messages.forEach { message ->
                appendLine("${if (message.role == MessageRole.USER) "用户" else "助手"}: ${message.content}")
                appendLine()
            }
            
            appendLine("请提供：")
            appendLine("1. 使用的编程语言和框架")
            appendLine("2. 实现的功能列表")
            appendLine("3. 采用的设计模式和架构")
            appendLine("4. 代码质量评估")
            appendLine("5. 性能考虑")
            appendLine("6. 安全考虑")
            appendLine("7. 技术债务和改进建议")
        }
    }
    
    private fun buildActionItemsPrompt(messages: List<EnhancedMessage>): String {
        return buildString {
            appendLine("请从以下对话中提取所有行动项：")
            appendLine()
            
            messages.forEach { message ->
                appendLine("${if (message.role == MessageRole.USER) "用户" else "助手"}: ${message.content}")
                appendLine()
            }
            
            appendLine("请识别并列出：")
            appendLine("1. 明确的任务和待办事项")
            appendLine("2. 承诺要做的事情")
            appendLine("3. 需要跟进的项目")
            appendLine("4. 每个行动项的优先级（高/中/低）")
            appendLine("5. 预计的完成时间（如果提到）")
        }
    }
    
    private fun buildDecisionPointsPrompt(messages: List<EnhancedMessage>): String {
        return buildString {
            appendLine("请识别对话中的所有决策点：")
            appendLine()
            
            messages.forEach { message ->
                appendLine("${if (message.role == MessageRole.USER) "用户" else "助手"}: ${message.content}")
                appendLine()
            }
            
            appendLine("请列出：")
            appendLine("1. 做出的决策")
            appendLine("2. 决策的理由")
            appendLine("3. 考虑过的替代方案")
            appendLine("4. 决策的影响")
            appendLine("5. 待决策的事项")
        }
    }
    
    private suspend fun callClaude(prompt: String): String {
        val response = StringBuilder()
        
        // TODO: 适配新的统一API - 需要重新实现
        // 使用新的统一服务执行查询
        // val result = unifiedSessionService.query(prompt, QueryOptions())
        // if (result.success) {
        //     result.sessionId?.let { sessionId ->
        //         unifiedSessionService.subscribeToSession(sessionId)
        //             .collect { messages ->
        //                 // 提取文本内容
        //                 messages.lastOrNull()?.content?.let { content ->
        //                     response.append(content)
        //                 }
        //             }
        //     }
        // }
        
        return response.toString()
    }
    
    private fun parseSummary(
        summaryText: String,
        messages: List<EnhancedMessage>,
        summaryType: SummaryType
    ): ChatSummary {
        // 简单解析，实际应该使用结构化输出
        val lines = summaryText.lines()
        val keyPoints = mutableListOf<String>()
        val nextSteps = mutableListOf<String>()
        val questions = mutableListOf<String>()
        
        var currentSection = ""
        lines.forEach { line ->
            when {
                line.contains("关键要点") || line.contains("要点") -> currentSection = "keyPoints"
                line.contains("下一步") || line.contains("建议") -> currentSection = "nextSteps"
                line.contains("问题") || line.contains("待解决") -> currentSection = "questions"
                line.startsWith("- ") || line.startsWith("• ") || line.matches(Regex("^\\d+\\..*")) -> {
                    val content = line.removePrefix("- ").removePrefix("• ").replace(Regex("^\\d+\\.\\s*"), "")
                    when (currentSection) {
                        "keyPoints" -> keyPoints.add(content)
                        "nextSteps" -> nextSteps.add(content)
                        "questions" -> questions.add(content)
                    }
                }
            }
        }
        
        return ChatSummary(
            summary = summaryText.lines().firstOrNull { it.isNotBlank() } ?: summaryText,
            summaryType = summaryType,
            messageCount = messages.size,
            timeRange = TimeRange(
                start = messages.firstOrNull()?.let { Instant.ofEpochMilli(it.timestamp) } ?: Instant.now(),
                end = messages.lastOrNull()?.let { Instant.ofEpochMilli(it.timestamp) } ?: Instant.now()
            ),
            keyPoints = keyPoints.takeIf { it.isNotEmpty() },
            nextSteps = nextSteps.takeIf { it.isNotEmpty() },
            questions = questions.takeIf { it.isNotEmpty() }
        )
    }
    
    private fun parseProgressSummary(response: String, messages: List<EnhancedMessage>): ProgressSummary {
        // 简单解析实现
        val completedTasks = mutableListOf<String>()
        val inProgressTasks = mutableListOf<String>()
        val pendingTasks = mutableListOf<String>()
        val issues = mutableListOf<Issue>()
        var progressPercentage = 0
        
        val lines = response.lines()
        var currentSection = ""
        
        lines.forEach { line ->
            when {
                line.contains("完成的任务") -> currentSection = "completed"
                line.contains("进行中") -> currentSection = "inProgress"
                line.contains("待处理") -> currentSection = "pending"
                line.contains("问题") -> currentSection = "issues"
                line.contains("进度") && line.contains("%") -> {
                    val match = Regex("(\\d+)%").find(line)
                    progressPercentage = match?.groupValues?.get(1)?.toIntOrNull() ?: 0
                }
                line.startsWith("- ") || line.matches(Regex("^\\d+\\..*")) -> {
                    val content = line.removePrefix("- ").replace(Regex("^\\d+\\.\\s*"), "")
                    when (currentSection) {
                        "completed" -> completedTasks.add(content)
                        "inProgress" -> inProgressTasks.add(content)
                        "pending" -> pendingTasks.add(content)
                        "issues" -> issues.add(Issue(description = content, status = IssueStatus.OPEN))
                    }
                }
            }
        }
        
        return ProgressSummary(
            completedTasks = completedTasks,
            inProgressTasks = inProgressTasks,
            pendingTasks = pendingTasks,
            issues = issues,
            progressPercentage = progressPercentage,
            lastUpdate = Instant.now()
        )
    }
    
    private fun parseTechnicalSummary(response: String, messages: List<EnhancedMessage>): TechnicalSummary {
        val languages = mutableSetOf<String>()
        val frameworks = mutableSetOf<String>()
        val features = mutableListOf<String>()
        val patterns = mutableListOf<String>()
        val improvements = mutableListOf<String>()
        
        // 简单解析实现
        val lines = response.lines()
        var currentSection = ""
        
        lines.forEach { line ->
            when {
                line.contains("编程语言") -> currentSection = "languages"
                line.contains("框架") -> currentSection = "frameworks"
                line.contains("功能") -> currentSection = "features"
                line.contains("模式") || line.contains("架构") -> currentSection = "patterns"
                line.contains("改进") || line.contains("建议") -> currentSection = "improvements"
                line.startsWith("- ") || line.matches(Regex("^\\d+\\..*")) -> {
                    val content = line.removePrefix("- ").replace(Regex("^\\d+\\.\\s*"), "")
                    when (currentSection) {
                        "languages" -> languages.add(content)
                        "frameworks" -> frameworks.add(content)
                        "features" -> features.add(content)
                        "patterns" -> patterns.add(content)
                        "improvements" -> improvements.add(content)
                    }
                }
            }
        }
        
        return TechnicalSummary(
            languages = languages.toList(),
            frameworks = frameworks.toList(),
            features = features,
            patterns = patterns,
            codeQuality = CodeQuality.GOOD, // 简化处理
            improvements = improvements
        )
    }
    
    private fun parseActionItems(response: String): List<ActionItem> {
        val actionItems = mutableListOf<ActionItem>()
        
        val lines = response.lines()
        lines.forEach { line ->
            if (line.startsWith("- ") || line.matches(Regex("^\\d+\\..*"))) {
                val content = line.removePrefix("- ").replace(Regex("^\\d+\\.\\s*"), "")
                
                // 解析优先级
                val priority = when {
                    content.contains("高") || content.contains("紧急") -> Priority.HIGH
                    content.contains("低") -> Priority.LOW
                    else -> Priority.MEDIUM
                }
                
                actionItems.add(
                    ActionItem(
                        description = content,
                        priority = priority,
                        status = ActionStatus.PENDING
                    )
                )
            }
        }
        
        return actionItems
    }
    
    private fun parseDecisionPoints(response: String): List<DecisionPoint> {
        val decisionPoints = mutableListOf<DecisionPoint>()
        
        // 简单解析实现
        val lines = response.lines()
        var currentDecision: String? = null
        var currentRationale: String? = null
        var alternatives = mutableListOf<String>()
        
        lines.forEach { line ->
            when {
                line.contains("决策：") -> {
                    currentDecision = line.substringAfter("决策：").trim()
                }
                line.contains("理由：") -> {
                    currentRationale = line.substringAfter("理由：").trim()
                }
                line.contains("替代方案：") -> {
                    // 开始收集替代方案
                }
                line.startsWith("- ") && currentDecision != null -> {
                    alternatives.add(line.removePrefix("- "))
                }
                line.isBlank() && currentDecision != null -> {
                    decisionPoints.add(
                        DecisionPoint(
                            decision = currentDecision!!,
                            rationale = currentRationale,
                            alternatives = alternatives.toList(),
                            timestamp = Instant.now()
                        )
                    )
                    currentDecision = null
                    currentRationale = null
                    alternatives.clear()
                }
            }
        }
        
        return decisionPoints
    }
    
    private fun generateCacheKey(
        messages: List<EnhancedMessage>,
        summaryType: SummaryType,
        options: SummaryOptions
    ): String {
        val messageIds = messages.map { it.id }.joinToString(",")
        return "$messageIds-${summaryType.name}-${options.hashCode()}"
    }
    
    private fun generateOverallSummary(segments: List<SegmentSummary>): String {
        return buildString {
            appendLine("整体对话包含 ${segments.size} 个段落：")
            segments.forEach { segment ->
                appendLine("- 段落 ${segment.segmentIndex + 1}: ${segment.summary}")
            }
        }
    }
}

/**
 * 总结类型
 */
enum class SummaryType(val description: String) {
    CONCISE("简洁"),
    DETAILED("详细"),
    TECHNICAL("技术"),
    EXECUTIVE("执行摘要")
}

/**
 * 总结选项
 */
data class SummaryOptions(
    val includeKeyPoints: Boolean = false,
    val includeNextSteps: Boolean = false,
    val includeQuestions: Boolean = false,
    val includeTimestamps: Boolean = false,
    val maxLength: Int? = null
)

/**
 * 对话总结
 */
data class ChatSummary(
    val summary: String,
    val summaryType: SummaryType,
    val messageCount: Int,
    val timeRange: TimeRange,
    val keyPoints: List<String>? = null,
    val nextSteps: List<String>? = null,
    val questions: List<String>? = null,
    val generatedAt: Instant = Instant.now()
)

/**
 * 分段总结
 */
data class SegmentedSummary(
    val totalSegments: Int,
    val segments: List<SegmentSummary>,
    val overallSummary: String
)

/**
 * 段落总结
 */
data class SegmentSummary(
    val segmentIndex: Int,
    val startMessageId: String,
    val endMessageId: String,
    val summary: String,
    val keyPoints: List<String>,
    val timestamp: Instant
)

/**
 * 进度总结
 */
data class ProgressSummary(
    val completedTasks: List<String>,
    val inProgressTasks: List<String>,
    val pendingTasks: List<String>,
    val issues: List<Issue>,
    val progressPercentage: Int,
    val lastUpdate: Instant
)

/**
 * 技术总结
 */
data class TechnicalSummary(
    val languages: List<String>,
    val frameworks: List<String>,
    val features: List<String>,
    val patterns: List<String>,
    val codeQuality: CodeQuality,
    val improvements: List<String>
)

/**
 * 行动项
 */
data class ActionItem(
    val id: String = UUID.randomUUID().toString(),
    val description: String,
    val priority: Priority,
    val status: ActionStatus,
    val assignee: String? = null,
    val dueDate: Instant? = null,
    val completedAt: Instant? = null
)

/**
 * 决策点
 */
data class DecisionPoint(
    val id: String = UUID.randomUUID().toString(),
    val decision: String,
    val rationale: String?,
    val alternatives: List<String>,
    val impact: String? = null,
    val timestamp: Instant
)

/**
 * 问题
 */
data class Issue(
    val id: String = UUID.randomUUID().toString(),
    val description: String,
    val status: IssueStatus,
    val resolution: String? = null,
    val resolvedAt: Instant? = null
)

/**
 * 时间范围
 */
data class TimeRange(
    val start: Instant,
    val end: Instant
) {
    val duration: Duration get() = Duration.between(start, end)
}

// 枚举类型

enum class Priority {
    HIGH, MEDIUM, LOW
}

enum class ActionStatus {
    PENDING, IN_PROGRESS, COMPLETED, CANCELLED
}

enum class IssueStatus {
    OPEN, IN_PROGRESS, RESOLVED, CLOSED
}

enum class CodeQuality {
    EXCELLENT, GOOD, FAIR, NEEDS_IMPROVEMENT
}

// 扩展函数
fun List<EnhancedMessage>.needsSummary(): Boolean {
    return size > 20 || sumOf { it.content.length } > 10000
}