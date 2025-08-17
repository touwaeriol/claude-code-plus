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
    modifier: Modifier = Modifier
) {
    // 计算总token使用量
    val totalTokens = remember(messageHistory, inputText, contexts) {
        val calculated = calculateTotalTokens(messageHistory, inputText, contexts)
        println("[ContextUsageIndicator] Token统计详情: 历史消息=${messageHistory.size}, 输入文本长度=${inputText.length}, 上下文=${contexts.size}, 总tokens=$calculated")
        
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
    
    // 悬浮提示内容 - 显示精确的token统计信息
    val tooltipText = buildString {
        append("上下文使用: ")
        append(String.format("%,d", totalTokens))
        append(" / ")
        append(String.format("%,d", maxTokens))
        append(" tokens (")
        append(percentage)
        append("%)")
        
        // 显示token来源分析
        val (preciseTokens, estimatedTokens) = analyzeTokenSources(messageHistory, inputText, contexts)
        if (preciseTokens > 0 || estimatedTokens > 0) {
            append("\n\n详细信息:")
            if (preciseTokens > 0) {
                append(String.format("\n• 精确统计: %,d tokens", preciseTokens))
            }
            if (estimatedTokens > 0) {
                append(String.format("\n• 估算部分: %,d tokens", estimatedTokens))
            }
        }
        
        when {
            percentage >= 95 -> append("\n\n⚠️ 超过限制")
            percentage >= 80 -> append("\n\n⚠️ 接近限制")
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
 * 这样既保证精确性又避免重复计算
 */
private fun calculateTotalTokens(
    messageHistory: List<EnhancedMessage>,
    inputText: String,
    contexts: List<ContextReference>
): Int {
    var totalTokens = 0
    
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