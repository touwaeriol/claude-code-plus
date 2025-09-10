/*
 * ContextUsageIndicator.kt
 * 
 * 上下文使用量指示器组件
 * 在输入框右下角显示当前上下文使用情况和百分比
 */

package com.claudecodeplus.ui.jewel.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import com.claudecodeplus.ui.models.AiModel
import com.claudecodeplus.ui.models.EnhancedMessage
import com.claudecodeplus.ui.models.ContextReference
import kotlin.math.roundToInt

/**
 * 上下文使用量指示器
 * 
 * @param currentModel 当前选择的模型
 * @param messageHistory 消息历史记录
 * @param inputText 当前输入文本
 * @param contexts 添加的上下文
 * @param modifier 修饰符
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContextUsageIndicator(
    currentModel: AiModel,
    messageHistory: List<EnhancedMessage> = emptyList(),
    inputText: String = "",
    contexts: List<ContextReference> = emptyList(),
    sessionTokenUsage: EnhancedMessage.TokenUsage? = null, // 会话级别的总token使用量
    modifier: Modifier = Modifier
) {
    // 计算总token使用量
    val totalTokens = remember(messageHistory, inputText, contexts, sessionTokenUsage) {
        // 如果有会话级别的总token使用量，优先使用它（最准确）
        if (sessionTokenUsage != null) {
            val sessionTotal = sessionTokenUsage.inputTokens + sessionTokenUsage.outputTokens
            println("[ContextUsageIndicator] 使用会话级别Token统计: input=${sessionTokenUsage.inputTokens}, output=${sessionTokenUsage.outputTokens}, total=$sessionTotal")
            sessionTotal
        } else {
            // 否则累加各个消息的token使用量，并传递sessionTokenUsage以便动态获取系统Token
            val calculated = calculateTotalTokens(messageHistory, inputText, contexts, sessionTokenUsage)
            println("[ContextUsageIndicator] 逐消息累加Token统计: 历史消息=${messageHistory.size}, 输入文本长度=${inputText.length}, 上下文=${contexts.size}, 总tokens=$calculated")
            
            // 简化的Token统计调试 - 只显示实际占用上下文的token
        var debugTotal = 0
        messageHistory.forEachIndexed { index, message ->
            if (message.tokenUsage != null) {
                val usage = message.tokenUsage!!
                val messageTotal = usage.inputTokens + usage.outputTokens
                debugTotal += messageTotal
                println("  - [$index] ${message.role}: input=${usage.inputTokens}, output=${usage.outputTokens}, 小计=$messageTotal")
            } else {
                val estimated = estimateTokensFromText(message.content)
                debugTotal += estimated
                println("  - [$index] ${message.role}: 估算=$estimated")
            }
        }
            println("  - 消息token总计: $debugTotal")
            println("  - 输入文本估算: ${estimateTokensFromText(inputText)}")
            println("  - 上下文估算: ${contexts.size * 1000}")  // 简化估算
            calculated
        }
    }
    
    val maxTokens = currentModel.contextLength
    val percentage = (totalTokens.toDouble() / maxTokens * 100).roundToInt()
    
    // 确定状态颜色
    val statusColor = when {
        percentage >= 95 -> Color(0xFFFF4444) // 错误红色
        percentage >= 80 -> Color(0xFFFF8800) // 警告橙色
        else -> JewelTheme.globalColors.text.normal.copy(alpha = 0.7f) // 正常灰色
    }
    
    val animatedColor by animateColorAsState(
        targetValue = statusColor,
        animationSpec = tween(300),
        label = "status color"
    )
    
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    
    // 格式化token数量显示
    val formattedTokens = formatTokenCount(totalTokens)
    val formattedMaxTokens = formatTokenCount(maxTokens)
    
    // 悬浮提示内容 - 显示精确的token统计信息和详细分解
    val tooltipText = buildString {
        append("上下文使用: ")
        append(String.format("%,d", totalTokens))
        append(" / ")
        append(String.format("%,d", maxTokens))
        append(" tokens (")
        append(percentage)
        append("%)")
        
        // 🎯 增强详细信息：显示Token组成分解
        append("\n\n📊 Token组成分解:")
        
        // 系统基础Token
        val systemTokens = if (sessionTokenUsage != null && sessionTokenUsage.cacheReadTokens > 0) {
            sessionTokenUsage.cacheReadTokens
        } else {
            val initMessage = messageHistory.firstOrNull { message ->
                message.role == com.claudecodeplus.ui.models.MessageRole.ASSISTANT && 
                message.tokenUsage != null && 
                message.tokenUsage!!.cacheReadTokens > 0
            }
            initMessage?.tokenUsage?.cacheReadTokens ?: 25926
        }
        append(String.format("\n• 系统基础: %,d tokens", systemTokens))
        append("\n  (系统提示词 + 工具定义)")
        
        // 对话历史Token
        val (preciseTokens, estimatedTokens) = analyzeTokenSources(messageHistory, inputText, contexts)
        val historyTokens = preciseTokens - kotlin.math.min(systemTokens, preciseTokens)
        if (historyTokens > 0) {
            append(String.format("\n• 对话历史: %,d tokens", historyTokens))
        }
        
        // 当前输入Token
        val inputTokens = estimateTokensFromText(inputText)
        if (inputTokens > 0) {
            append(String.format("\n• 当前输入: %,d tokens", inputTokens))
        }
        
        // 上下文文件Token
        val contextTokens = contexts.sumOf { context ->
            when (context) {
                is ContextReference.FileReference -> 1000
                is ContextReference.WebReference -> 2000
                else -> 500
            }.toLong()
        }.toInt()
        if (contextTokens > 0) {
            append(String.format("\n• 上下文文件: %,d tokens (%d个文件)", contextTokens, contexts.size))
        }
        
        // 估算Token提示
        if (estimatedTokens > systemTokens) {
            val userEstimatedTokens = estimatedTokens - systemTokens
            append(String.format("\n\n📝 估算精度: %,d tokens 为精确统计", preciseTokens))
            if (userEstimatedTokens > 0) {
                append(String.format("\n  %,d tokens 为估算值", userEstimatedTokens))
            }
        }
        
        // 🎯 缓存优化说明（如果有缓存Token数据）
        if (sessionTokenUsage != null && sessionTokenUsage.cacheCreationTokens > 0) {
            append("\n\n⚡ 缓存优化:")
            append(String.format("\n• 缓存创建: %,d tokens", sessionTokenUsage.cacheCreationTokens))
            if (sessionTokenUsage.cacheReadTokens > 0) {
                append(String.format("\n• 缓存复用: %,d tokens", sessionTokenUsage.cacheReadTokens))
                val savings = sessionTokenUsage.cacheCreationTokens - sessionTokenUsage.cacheReadTokens
                if (savings > 0) {
                    append(String.format("\n• 节省计费: %,d tokens", savings))
                }
            }
            append("\n  (缓存仅影响计费，不额外占用上下文)")
        }
        
        when {
            percentage >= 95 -> append("\n\n⚠️ 上下文即将用完！")
            percentage >= 80 -> append("\n\n⚠️ 上下文接近限制")
            percentage >= 50 -> append("\n\n💡 可考虑开启新对话")
        }
    }
    
    Tooltip(
        tooltip = {
            Text(
                text = tooltipText,
                style = JewelTheme.defaultTextStyle.copy(fontSize = 11.sp)
            )
        }
    ) {
        Row(
            modifier = modifier
                .clip(RoundedCornerShape(4.dp))
                .background(
                    if (isHovered) 
                        JewelTheme.globalColors.panelBackground.copy(alpha = 0.8f)
                    else 
                        Color.Transparent
                )
                .border(
                    width = if (isHovered) 1.dp else 0.dp,
                    color = JewelTheme.globalColors.borders.normal.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(4.dp)
                )
                .hoverable(interactionSource)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 简洁格式：[2.4k/200k]
            Text(
                text = "[$formattedTokens/$formattedMaxTokens]",
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 11.sp,
                    color = animatedColor
                )
            )
        }
    }
}

/**
 * 计算总token使用量
 * 
 * 优先使用Claude CLI提供的精确token数据，必要时进行估算
 * 重要：包含系统提示词、工具定义等基础上下文开销
 */
private fun calculateTotalTokens(
    messageHistory: List<EnhancedMessage>,
    inputText: String,
    contexts: List<ContextReference>,
    sessionTokenUsage: EnhancedMessage.TokenUsage? = null
): Int {
    var totalTokens = 0
    
    // 0. Claude Code系统级基础Token开销（这部分通常被缓存，但仍占用上下文）
    // 优先从实际会话数据获取，否则使用基于真实数据的默认值
    val systemBaseTokens = getSystemBaseTokens(messageHistory, sessionTokenUsage)
    totalTokens += systemBaseTokens
    
    // 1. 历史消息的精确token统计
    messageHistory.forEach { message ->
        if (message.tokenUsage != null) {
            // 使用Claude CLI提供的精确token数据
            val usage = message.tokenUsage!!
            
            // 正确的上下文token计算：只计算实际内容token，不包括缓存机制token
            // inputTokens 和 outputTokens 代表实际占用的上下文空间
            // cacheCreationTokens 和 cacheReadTokens 只是计费机制，不额外占用上下文
            totalTokens += usage.inputTokens + usage.outputTokens
        } else {
            // 回退到估算（用于用户消息或无token数据的消息）
            totalTokens += estimateTokensFromText(message.content)
            
            // 工具调用结果的估算
            message.toolCalls.forEach { toolCall ->
                totalTokens += estimateTokensFromText(toolCall.parameters.toString())
                toolCall.result?.let { result ->
                    when (result) {
                        is com.claudecodeplus.ui.models.ToolResult.Success -> {
                            totalTokens += estimateTokensFromText(result.output)
                        }
                        is com.claudecodeplus.ui.models.ToolResult.Failure -> {
                            totalTokens += estimateTokensFromText(result.error)
                        }
                        else -> {
                            totalTokens += estimateTokensFromText(result.toString())
                        }
                    }
                }
            }
        }
    }
    
    // 2. 当前用户输入（估算）
    totalTokens += estimateTokensFromText(inputText)
    
    // 3. 上下文文件（估算）
    contexts.forEach { context ->
        when (context) {
            is ContextReference.FileReference -> {
                // 文件大小的简单估算，实际应该读取文件内容
                totalTokens += 1000 // 平均每个文件1000 tokens
            }
            is ContextReference.WebReference -> {
                totalTokens += 2000 // 网页内容估算
            }
            else -> {
                totalTokens += 500 // 其他上下文类型
            }
        }
    }
    
    return totalTokens
}

/**
 * 基于文本估算token数量
 * 简化算法：英文约 4 字符 = 1 token，中文约 1-1.5 字符 = 1 token
 */
private fun estimateTokensFromText(text: String): Int {
    if (text.isBlank()) return 0
    
    var chineseChars = 0
    var englishChars = 0
    
    text.forEach { char ->
        when {
            char.isChineseCharacter() -> chineseChars++
            char.isLetterOrDigit() || char.isWhitespace() -> englishChars++
        }
    }
    
    // 中文字符按 1.2 字符 = 1 token，英文按 4 字符 = 1 token 计算
    return (chineseChars / 1.2 + englishChars / 4.0).roundToInt()
}

/**
 * 判断是否是中文字符
 */
private fun Char.isChineseCharacter(): Boolean {
    return this.code in 0x4E00..0x9FFF // 基本汉字 Unicode 范围
}

/**
 * 分析token来源
 * 返回 (精确统计的tokens, 估算的tokens)
 */
private fun analyzeTokenSources(
    messageHistory: List<EnhancedMessage>,
    inputText: String,
    contexts: List<ContextReference>
): Pair<Int, Int> {
    var preciseTokens = 0
    var estimatedTokens = 0
    
    // 分析历史消息
    messageHistory.forEach { message ->
        if (message.tokenUsage != null) {
            preciseTokens += message.tokenUsage!!.inputTokens + message.tokenUsage!!.outputTokens
        } else {
            estimatedTokens += estimateTokensFromText(message.content)
            message.toolCalls.forEach { toolCall ->
                estimatedTokens += estimateTokensFromText(toolCall.parameters.toString())
                toolCall.result?.let { result ->
                    when (result) {
                        is com.claudecodeplus.ui.models.ToolResult.Success -> {
                            estimatedTokens += estimateTokensFromText(result.output)
                        }
                        is com.claudecodeplus.ui.models.ToolResult.Failure -> {
                            estimatedTokens += estimateTokensFromText(result.error)
                        }
                        else -> {
                            estimatedTokens += estimateTokensFromText(result.toString())
                        }
                    }
                }
            }
        }
    }
    
    // 当前输入和上下文都是估算的
    estimatedTokens += estimateTokensFromText(inputText)
    contexts.forEach { context ->
        estimatedTokens += when (context) {
            is ContextReference.FileReference -> 1000
            is ContextReference.WebReference -> 2000
            else -> 500
        }
    }
    
    return Pair(preciseTokens, estimatedTokens)
}

/**
 * 获取Claude Code系统级基础Token开销
 * 包括：系统提示词、工具定义、环境信息等
 * 
 * 优先从SessionObject获取动态数据，否则使用基于真实会话数据的默认值
 */
private fun getSystemBaseTokens(
    messageHistory: List<EnhancedMessage>,
    sessionTokenUsage: EnhancedMessage.TokenUsage?
): Int {
    // 🎯 策略1：从会话级别Token统计中获取系统基础Token
    if (sessionTokenUsage != null && sessionTokenUsage.cacheReadTokens > 0) {
        // cache_read_input_tokens 表示系统缓存实际占用的上下文空间
        println("  - 动态系统Token（来源：会话级统计）: ${sessionTokenUsage.cacheReadTokens}")
        return sessionTokenUsage.cacheReadTokens
    }
    
    // 🎯 策略2：从历史消息中查找第一条Claude init消息的Token数据
    val initMessage = messageHistory.firstOrNull { message ->
        message.role == com.claudecodeplus.ui.models.MessageRole.ASSISTANT && 
        message.tokenUsage != null && 
        message.tokenUsage!!.cacheReadTokens > 0
    }
    
    if (initMessage?.tokenUsage != null) {
        val systemTokens = initMessage.tokenUsage!!.cacheReadTokens
        println("  - 动态系统Token（来源：init消息）: $systemTokens")
        return systemTokens
    }
    
    // 🎯 策略3：使用基于真实会话数据的默认值作为回退
    // 数据来源：分析 ~/.claude/projects 中的实际会话历史文件
    // session: 843ebfc6-9548-406f-856f-c5d74cb4e41b
    // cache_read_input_tokens: 25,926 (后续读取系统缓存的准确值)
    println("  - 默认系统Token（来源：历史数据分析）: 25926")
    return 25926 // 基于真实会话数据的精确值
}

/**
 * 格式化token数量显示
 * < 1000: 显示具体数字
 * >= 1000: 显示 k 格式，保留一位小数
 */
private fun formatTokenCount(tokens: Int): String {
    return when {
        tokens < 1000 -> tokens.toString()
        tokens < 10000 -> String.format("%.1fk", tokens / 1000.0)
        else -> String.format("%.0fk", tokens / 1000.0)
    }
}