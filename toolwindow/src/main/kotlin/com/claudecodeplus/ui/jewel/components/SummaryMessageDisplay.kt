package com.claudecodeplus.ui.jewel.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudecodeplus.ui.models.EnhancedMessage
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.Outline

/**
 * Claude Summary消息显示组件
 * 
 * 基于Claudia项目的SummaryWidget设计，用于显示AI生成的会话压缩摘要。
 * 
 * 特点：
 * - 蓝色边框和背景，视觉上区别于普通消息
 * - 信息图标标识
 * - "AI Summary"标签
 * - 显示摘要内容和leafUuid
 * 
 * @param message 包含summary信息的EnhancedMessage
 * @param modifier Compose修饰符
 */
@Composable
fun SummaryMessageDisplay(
    message: EnhancedMessage,
    modifier: Modifier = Modifier
) {
    // 确保这是一个Summary消息
    if (!message.isSummary || message.leafUuid == null) {
        return
    }
    
    // 蓝色主题色彩定义
    val primaryBlue = Color(0xFF3B82F6)
    val lightBlue = primaryBlue.copy(alpha = 0.05f)
    val borderBlue = primaryBlue.copy(alpha = 0.2f)
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = borderBlue,
                shape = RoundedCornerShape(8.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 头部：图标 + 标题
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 信息图标区域
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .border(
                            width = 1.dp, 
                            color = primaryBlue.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "ℹ️",
                        style = JewelTheme.defaultTextStyle.copy(fontSize = 16.sp)
                    )
                }
                
                // 标题区域
                Column {
                    Text(
                        text = "AI Summary",
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 12.sp,
                            color = primaryBlue
                        )
                    )
                }
            }
            
            // 摘要内容
            SelectionContainer {
                Text(
                    text = message.content,
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 14.sp,
                        color = JewelTheme.globalColors.text.normal
                    )
                )
            }
            
            // leafUuid显示（类似Claudia的ID显示）
            SelectionContainer {
                Text(
                    text = "ID: ${message.leafUuid!!.take(8)}...",
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 11.sp,
                        color = JewelTheme.globalColors.text.normal.copy(alpha = 0.6f),
                        fontFamily = FontFamily.Monospace
                    )
                )
            }
        }
    }
}

/**
 * 判断消息是否应该显示为Summary格式
 * 
 * 实现与Claudia相同的判断逻辑：
 * - message.leafUuid && message.summary && message.type === "summary"
 * 
 * @param message 要判断的消息
 * @return 是否为Summary消息
 */
fun isSummaryMessage(message: EnhancedMessage): Boolean {
    return message.isSummary && 
           message.leafUuid != null && 
           message.content.isNotBlank()
}