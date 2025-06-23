package com.claudecodeplus.ui.redesign.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Text

/**
 * 对话窗口顶部工具栏
 */
@Composable
fun ConversationToolBar(
    onRefresh: () -> Unit,
    onNewChat: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF3C3F41))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 标题
        Text(
            "Claude Code Plus",
            style = JewelTheme.defaultTextStyle.copy(
                color = Color(0xFFBBBBBB)
            )
        )
        
        // 操作按钮
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 刷新按钮
            IconButton(onClick = onRefresh) {
                Text("🔄") // 临时使用表情符号
            }
            
            // 新建对话按钮
            IconButton(onClick = onNewChat) {
                Text("➕")
            }
            
            // 设置按钮
            IconButton(onClick = onSettings) {
                Text("⚙️")
            }
        }
    }
}