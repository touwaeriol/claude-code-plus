/*
 * IconUtils.kt
 * 
 * 图标加载工具类 - 处理 Jewel 组件的图标加载问题
 */

package com.claudecodeplus.ui.jewel.components.utils

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

/**
 * 图标工具类
 * 使用纯文本符号替代图标，避免类加载器冲突
 */
object IconUtils {
    
    /**
     * 下拉箭头 - 使用 Unicode 箭头字符
     */
    @Composable
    fun ChevronDown(
        enabled: Boolean = true,
        color: Color = if (enabled) JewelTheme.globalColors.text.normal else JewelTheme.globalColors.text.disabled
    ) {
        Text(
            text = "▼", // U+25BC Black Down-Pointing Triangle
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 10.sp,
                color = color
            )
        )
    }
}

/**
 * 用于 ComboBox 的箭头图标
 */
@Composable
fun ComboBoxChevronIcon(
    enabled: Boolean = true
) {
    IconUtils.ChevronDown(enabled = enabled)
}