/*
 * ChatInputArea.kt
 * 
 * 聊天输入区域组件 - 使用统一的 UnifiedInputArea 组件
 */

package com.claudecodeplus.ui.jewel.components

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import com.claudecodeplus.ui.models.*
import com.claudecodeplus.ui.jewel.components.context.*
import com.claudecodeplus.ui.services.FileIndexService
import com.claudecodeplus.ui.services.ProjectService

/**
 * 聊天输入区域组件
 * 使用统一的 UnifiedInputArea 组件的输入模式
 */
@Composable
fun ChatInputArea(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSend: () -> Unit,
    onStop: (() -> Unit)? = null,
    contexts: List<ContextReference> = emptyList(),
    onContextAdd: (ContextReference) -> Unit = {},
    onContextRemove: (ContextReference) -> Unit = {},
    isGenerating: Boolean = false,
    enabled: Boolean = true,
    selectedModel: AiModel = AiModel.OPUS,
    onModelChange: (AiModel) -> Unit = {},
    fileIndexService: FileIndexService? = null,
    projectService: ProjectService? = null,
    inlineReferenceManager: InlineReferenceManager = remember { InlineReferenceManager() },
    modifier: Modifier = Modifier
) {
    UnifiedInputArea(
        mode = InputAreaMode.INPUT,
        value = value,
        onValueChange = onValueChange,
        onSend = onSend,
        onStop = onStop,
        contexts = contexts,
        onContextAdd = onContextAdd,
        onContextRemove = onContextRemove,
        isGenerating = isGenerating,
        enabled = enabled,
        selectedModel = selectedModel,
        onModelChange = onModelChange,
        fileIndexService = fileIndexService,
        projectService = projectService,
        inlineReferenceManager = inlineReferenceManager,
        modifier = modifier
    )
}

 