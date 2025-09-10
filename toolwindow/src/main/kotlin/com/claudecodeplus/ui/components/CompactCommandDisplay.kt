package com.claudecodeplus.ui.jewel.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import com.claudecodeplus.ui.jewel.components.tools.JumpingDots

/**
 * 压缩命令的状态
 */
enum class CompactCommandStatus {
    INITIATED,    // 用户输入了 /compact 命令
    PROCESSING,   // 正在处理压缩
    COMPLETED     // 压缩完成
}

/**
 * 压缩命令展示组件
 * 用于显示 /compact 命令的执行状态
 */
@Composable
fun CompactCommandDisplay(
    status: CompactCommandStatus,
    message: String = "",
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                when (status) {
                    CompactCommandStatus.INITIATED -> Color(0xFF1976D2).copy(alpha = 0.1f)  // 蓝色
                    CompactCommandStatus.PROCESSING -> Color(0xFFF57C00).copy(alpha = 0.1f)  // 橙色
                    CompactCommandStatus.COMPLETED -> Color(0xFF388E3C).copy(alpha = 0.1f)   // 绿色
                }
            )
            .border(
                1.dp,
                when (status) {
                    CompactCommandStatus.INITIATED -> Color(0xFF1976D2).copy(alpha = 0.3f)  // 蓝色
                    CompactCommandStatus.PROCESSING -> Color(0xFFF57C00).copy(alpha = 0.3f)  // 橙色
                    CompactCommandStatus.COMPLETED -> Color(0xFF388E3C).copy(alpha = 0.3f)   // 绿色
                },
                RoundedCornerShape(8.dp)
            )
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 状态图标
            when (status) {
                CompactCommandStatus.INITIATED -> {
                    Icon(
                        key = AllIconsKeys.Actions.Refresh,
                        contentDescription = "压缩命令",
                        modifier = Modifier.size(20.dp),
                        tint = Color(0xFF1976D2)  // 蓝色
                    )
                }
                CompactCommandStatus.PROCESSING -> {
                    JumpingDots(
                        modifier = Modifier.size(20.dp),
                        color = Color(0xFFF57C00)  // 橙色
                    )
                }
                CompactCommandStatus.COMPLETED -> {
                    Icon(
                        key = AllIconsKeys.Actions.Commit,
                        contentDescription = "压缩完成",
                        modifier = Modifier.size(20.dp),
                        tint = Color(0xFF388E3C)  // 绿色
                    )
                }
            }
            
            // 状态文本
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = when (status) {
                        CompactCommandStatus.INITIATED -> "/compact"
                        CompactCommandStatus.PROCESSING -> "正在压缩会话..."
                        CompactCommandStatus.COMPLETED -> "会话压缩完成"
                    },
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = when (status) {
                            CompactCommandStatus.INITIATED -> Color(0xFF1976D2)  // 蓝色
                            CompactCommandStatus.PROCESSING -> Color(0xFFF57C00)  // 橙色
                            CompactCommandStatus.COMPLETED -> Color(0xFF388E3C)   // 绿色
                        }
                    )
                )
                
                if (message.isNotBlank()) {
                    Text(
                        text = message,
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 12.sp,
                            color = JewelTheme.globalColors.text.normal.copy(alpha = 0.7f)
                        )
                    )
                }
                
                // 完成状态的提示
                if (status == CompactCommandStatus.COMPLETED) {
                    Text(
                        text = "💡 按 Ctrl+R 查看完整摘要",
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 11.sp,
                            color = JewelTheme.globalColors.text.normal.copy(alpha = 0.6f)
                        )
                    )
                }
            }
            
            // 处理中的时间提示
            if (status == CompactCommandStatus.PROCESSING) {
                Text(
                    text = "约需 30 秒",
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 11.sp,
                        color = JewelTheme.globalColors.text.disabled
                    )
                )
            }
        }
    }
}

/**
 * 从消息内容中解析压缩命令状态
 */
fun parseCompactCommandStatus(content: String): CompactCommandStatus? {
    return when {
        content.contains("<command-name>/compact</command-name>") -> {
            when {
                content.contains("<local-command-stdout>Compacted. ctrl+r to see full summary</local-command-stdout>") -> 
                    CompactCommandStatus.COMPLETED
                content.contains("<command-message>compact</command-message>") -> 
                    CompactCommandStatus.PROCESSING
                else -> 
                    CompactCommandStatus.INITIATED
            }
        }
        else -> null
    }
}

/**
 * 判断消息是否是压缩命令相关消息
 */
fun isCompactCommandMessage(content: String): Boolean {
    return content.contains("<command-name>/compact</command-name>") ||
           content.contains("Caveat:") && content.contains("local commands")
}