/*
 * SendStopButton.kt
 * 
 * 发送/停止按钮组件 - 现代化设计风格
 */

package com.claudecodeplus.ui.jewel.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.claudecodeplus.ui.services.stringResource
import com.claudecodeplus.ui.icons.ClaudeIcons
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import java.io.File

/**
 * 发送/停止按钮组合组件
 * 包含上下文使用量指示器、图片选择按钮和发送按钮
 * 
 * @param isGenerating 是否正在生成
 * @param onSend 发送消息回调
 * @param onStop 停止生成回调
 * @param onImageSelected 图片选择回调
 * @param hasInput 是否有输入内容
 * @param enabled 是否启用
 * @param currentModel 当前选择的模型（用于上下文长度计算）
 * @param messageHistory 消息历史（用于上下文统计）
 * @param inputText 当前输入文本
 * @param contexts 添加的上下文
 * @param sessionTokenUsage 会话级别的Token使用量（从CLI result消息获取）
 * @param modifier 修饰符
 */
@Composable
fun SendStopButtonGroup(
    isGenerating: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onImageSelected: (File) -> Unit = {},
    hasInput: Boolean = true,
    enabled: Boolean = true,
    currentModel: com.claudecodeplus.ui.models.AiModel = com.claudecodeplus.ui.models.AiModel.OPUS,
    messageHistory: List<com.claudecodeplus.ui.models.EnhancedMessage> = emptyList(),
    inputText: String = "",
    contexts: List<com.claudecodeplus.ui.models.ContextReference> = emptyList(),
    sessionTokenUsage: com.claudecodeplus.ui.models.EnhancedMessage.TokenUsage? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 上下文使用量指示器
        ContextUsageIndicator(
            currentModel = currentModel,
            messageHistory = messageHistory,
            inputText = inputText,
            contexts = contexts,
            sessionTokenUsage = sessionTokenUsage
        )
        
        // 图片选择按钮
        ImagePickerButton(
            onImageSelected = onImageSelected,
            enabled = enabled && !isGenerating,
            modifier = Modifier.size(36.dp)
        )
        
        // 发送/停止按钮
        SendStopButton(
            isGenerating = isGenerating,
            onSend = onSend,
            onStop = onStop,
            hasInput = hasInput,
            enabled = enabled,
            modifier = Modifier.size(36.dp)
        )
    }
}

/**
 * 使用Jewel原生组件的发送/停止按钮
 * 支持右键菜单和中断发送功能
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SendStopButton(
    isGenerating: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onInterruptAndSend: (() -> Unit)? = null,
    hasInput: Boolean = true,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    var showContextMenu by remember { mutableStateOf(false) }
    
    // 确定当前图标
    val iconKey = when {
        isGenerating && !hasInput -> ClaudeIcons.Actions.stop  // 停止
        else -> ClaudeIcons.Actions.send  // 发送
    }
    
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .clickable(enabled = enabled && (hasInput || isGenerating)) {
                    when {
                        isGenerating && !hasInput -> onStop()  // 停止生成
                        isGenerating && hasInput -> onSend()   // 添加到队列
                        hasInput -> onSend()                   // 直接发送
                    }
                }
                .background(
                    if (enabled && (hasInput || isGenerating))
                        JewelTheme.globalColors.borders.focused
                    else
                        JewelTheme.globalColors.borders.focused.copy(alpha = 0.3f),
                    CircleShape
                )
                .padding(8.dp),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Icon(
                key = iconKey,
                contentDescription = if (isGenerating && !hasInput) "停止生成" else "发送消息",
                tint = if (enabled && (hasInput || isGenerating)) 
                    JewelTheme.globalColors.panelBackground
                else 
                    JewelTheme.globalColors.text.disabled,
                modifier = Modifier.size(20.dp)
            )
        }
        
        // 右键菜单
        if (showContextMenu) {
            Popup(
                onDismissRequest = { showContextMenu = false },
                alignment = Alignment.TopEnd
            ) {
                Column(
                    modifier = Modifier
                        .background(JewelTheme.globalColors.panelBackground)
                        .border(
                            1.dp,
                            JewelTheme.globalColors.borders.normal,
                            RoundedCornerShape(4.dp)
                        )
                        .clip(RoundedCornerShape(4.dp))
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSend()
                                showContextMenu = false
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(stringResource("send"), style = JewelTheme.defaultTextStyle)
                    }
                    if (onInterruptAndSend != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onInterruptAndSend()
                                    showContextMenu = false
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(stringResource("interrupt_and_send"), style = JewelTheme.defaultTextStyle)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 使用Jewel原生组件的图片选择按钮
 */
@Composable
fun ImagePickerButton(
    onImageSelected: (File) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clickable(enabled = enabled) {
                showImagePicker(onImageSelected)
            }
            .background(
                if (enabled)
                    JewelTheme.globalColors.borders.focused.copy(alpha = 0.15f)
                else
                    JewelTheme.globalColors.borders.focused.copy(alpha = 0.05f),
                CircleShape
            )
            .padding(8.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Icon(
            key = ClaudeIcons.Actions.selectImage,
            contentDescription = "选择图片",
            tint = if (enabled) 
                JewelTheme.globalColors.borders.focused 
            else 
                JewelTheme.globalColors.text.disabled,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * 显示图片选择器
 */
private fun showImagePicker(onImageSelected: (File) -> Unit) {
    try {
        val fileChooser = JFileChooser()
        fileChooser.dialogTitle = stringResource("select_image")
        fileChooser.fileSelectionMode = JFileChooser.FILES_ONLY
        
        // 设置文件过滤器，只显示图片文件
        val imageFilter = FileNameExtensionFilter(
            stringResource("image_files"),
            "jpg", "jpeg", "png", "gif", "bmp", "webp"
        )
        fileChooser.fileFilter = imageFilter
        
        // 显示对话框
        val result = fileChooser.showOpenDialog(null)
        if (result == JFileChooser.APPROVE_OPTION) {
            val selectedFile = fileChooser.selectedFile
            if (selectedFile != null && selectedFile.exists()) {
                onImageSelected(selectedFile)
            }
        }
    } catch (e: Exception) {
        // 图片选择错误: ${e.message}
        e.printStackTrace()
    }
} 