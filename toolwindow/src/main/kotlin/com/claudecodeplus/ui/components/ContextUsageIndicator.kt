/*
 * ContextUsageIndicator.kt
 * 
 * 上下文使用量指示器组件
 * 在输入框右下角显示当前上下文使用情况和百分比
 */

package com.claudecodeplus.ui.jewel.components

import com.claudecodeplus.core.logging.*
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
    // 🎯 基于会话日志分析的精确Token统计
    val totalTokens = remember(messageHistory, inputText, contexts, sessionTokenUsage) {
        calculateAccurateTokens(messageHistory, inputText, contexts, sessionTokenUsage)
    }
    
    val maxTokens = currentModel.contextLength
    val percentage = (totalTokens.toDouble() / maxTokens * 100).roundToInt()
    
    // 🎯 基于Claude Code的92%阈值系统
    val statusColor = when {
        percentage >= 95 -> Color(0xFFFF4444) // 危险红色 - 临界状态
        percentage >= 92 -> Color(0xFFFF8800) // 警告橙色 - Claude Code自动压缩阈值
        percentage >= 75 -> Color(0xFFFFA500) // 注意黄色 - 接近阈值
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

        // 📊 统计说明 - 完全基于Claude Code原理
        append("\n\n📊 统计原理:")
        val messageCount = messageHistory.size
        if (messageCount > 0) {
            append("\n• 基于Claude Code的VE→HY5→zY5函数链")
            append("\n• VE: 逆序遍历找最新assistant消息")
            append("\n• HY5: 过滤synthetic消息，取真实API调用")
            append("\n• zY5: 累加input+output+cache_creation+cache_read")
            append("\n• 这个总数用于Claude Code的92%阈值判断")
        } else {
            append("\n• 新会话，暂无API调用数据")
            append("\n• 首次调用将显示完整token消耗")
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
            append("\n  (cache tokens占用上下文窗口，同时影响计费)")
        }

        when {
            percentage >= 95 -> append("\n\n🚨 上下文窗口即将用完！建议立即开启新对话")
            percentage >= 92 -> append("\n\n⚠️ 已达到Claude Code的92%自动压缩阈值")
            percentage >= 75 -> append("\n\n💡 接近92%阈值，可考虑开启新对话")
            percentage >= 50 -> append("\n\n💡 上下文已使用一半，注意管理")
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
 * 🎯 基于Claude Code原理的精确Token统计
 *
 * 完全按照Claude Code的VE+HY5+zY5函数链实现：
 * 1. VE函数：逆序遍历消息，找到最新assistant消息的usage
 * 2. HY5函数：过滤synthetic消息，只取真实API调用数据
 * 3. zY5函数：累加所有token类型得到总数
 *
 * 关键理解：这个总数不是当前上下文大小，而是最新API调用的完整token消耗
 * - input_tokens = 当前完整上下文大小（系统提示+历史+用户输入）
 * - 其他tokens = output + cache相关
 * - Claude Code用这个总数来判断92%阈值
 */
private fun calculateAccurateTokens(
    messageHistory: List<EnhancedMessage>,
    inputText: String,
    contexts: List<ContextReference>,
    sessionTokenUsage: EnhancedMessage.TokenUsage? = null
): Int {
    logD("\n🔧 [ContextUsage] 基于Claude Code原理的Token统计...")

    // 🎯 实现Claude Code的VE函数：逆序遍历找最新usage
    val latestUsage = findLatestTokenUsage(messageHistory)

    if (latestUsage != null) {
        // 🎯 实现Claude Code的zY5函数：累加所有token类型
        val totalTokens = calculateTotalTokens(latestUsage)

    logD("    - 基于最新API调用的token统计:")
    logD("      • input_tokens: ${latestUsage.inputTokens}（当前完整上下文）")
    logD("      • output_tokens: ${latestUsage.outputTokens}（AI回复）")
    logD("      • cache_creation_tokens: ${latestUsage.cacheCreationTokens}")
    logD("      • cache_read_tokens: ${latestUsage.cacheReadTokens}")
    logD("      • 总计: $totalTokens tokens（用于92%判断的数字）")
    logD("      ✅ 与Claude Code的VE→HY5→zY5函数链完全一致")

        return totalTokens
    } else {
    logD("    - 新会话，暂无API调用数据，显示0 tokens")
        return 0
    }
}

/**
 * 🎯 实现Claude Code的VE函数：逆序遍历找最新token usage
 * 对应源码：function VE(A) { let B = A.length - 1; while (B >= 0) ... }
 */
private fun findLatestTokenUsage(messageHistory: List<EnhancedMessage>): EnhancedMessage.TokenUsage? {
    // 逆序遍历消息数组，找第一个有usage的assistant消息
    for (i in messageHistory.size - 1 downTo 0) {
        val message = messageHistory[i]
        // 🎯 实现Claude Code的HY5函数：过滤条件
        if (isValidAssistantMessage(message)) {
            return message.tokenUsage
        }
    }
    return null
}

/**
 * 🎯 实现Claude Code的HY5函数：验证assistant消息有效性
 * 对应源码：A?.type === "assistant" && "usage" in A.message && !(synthetic)
 */
private fun isValidAssistantMessage(message: EnhancedMessage): Boolean {
    return message.role == com.claudecodeplus.ui.models.MessageRole.ASSISTANT &&
           message.tokenUsage != null &&
           !message.content.contains("<synthetic>") // 排除合成消息
}

/**
 * 🎯 实现Claude Code的zY5函数：计算总token数
 * 对应源码：A.input_tokens + (A.cache_creation_input_tokens ?? 0) + ...
 */
private fun calculateTotalTokens(usage: EnhancedMessage.TokenUsage): Int {
    return usage.inputTokens +
           usage.outputTokens +
           usage.cacheCreationTokens +
           usage.cacheReadTokens
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
