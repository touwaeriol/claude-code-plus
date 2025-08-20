/*
 * ModernSelectors.kt
 * 
 * 现代化选择器组件 - 悬浮卡片式设计
 * 包含模型选择器和权限模式选择器
 */

package com.claudecodeplus.ui.jewel.components

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.claudecodeplus.ui.models.AiModel
import com.claudecodeplus.ui.models.PermissionMode

/**
 * 现代化模型选择器
 * 为了兼容性，这里提供别名
 */
@Composable
fun ModernModelSelector(
    currentModel: AiModel,
    onModelChange: (AiModel) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    ChatInputModelSelector(
        currentModel = currentModel,
        onModelChange = onModelChange,
        enabled = enabled,
        modifier = modifier
    )
}

/**
 * 现代化权限模式选择器
 * 为了兼容性，这里提供别名
 */
@Composable
fun ModernPermissionSelector(
    currentMode: PermissionMode,
    onModeChange: (PermissionMode) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    ChatInputPermissionSelector(
        currentPermissionMode = currentMode,
        onPermissionModeChange = onModeChange,
        enabled = enabled,
        modifier = modifier
    )
}