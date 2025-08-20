/*
 * ChatInputContextSelectorPopup.kt
 * 
 * 为聊天输入区域设计的简化版上下文选择器弹出组件
 */

package com.claudecodeplus.ui.jewel.components.context

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.claudecodeplus.ui.models.*
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

/**
 * 简化的上下文选择器弹出组件
 * 专为聊天输入区域设计，提供文件和网页上下文选择
 */
@Composable
fun ChatInputContextSelectorPopup(
    onDismiss: () -> Unit,
    onContextSelect: (ContextReference) -> Unit,
    searchService: ContextSearchService,
    modifier: Modifier = Modifier
) {
    // 简化版本：直接显示提示信息
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "上下文选择器（简化版）",
            style = JewelTheme.defaultTextStyle.copy(
                color = JewelTheme.globalColors.text.normal
            )
        )
        Text(
            text = "功能开发中...",
            style = JewelTheme.defaultTextStyle.copy(
                color = JewelTheme.globalColors.text.disabled
            )
        )
    }
}