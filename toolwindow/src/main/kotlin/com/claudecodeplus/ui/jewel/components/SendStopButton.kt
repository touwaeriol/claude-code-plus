/*
 * SendStopButton.kt
 * 
 * 发送/停止按钮组件 - 现代化设计风格
 */

package com.claudecodeplus.ui.jewel.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import java.io.File
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.window.Popup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.window.PopupProperties

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
            modifier = Modifier.size(24.dp)
        )
        
        // 发送/停止按钮
        SendStopButton(
            isGenerating = isGenerating,
            onSend = onSend,
            onStop = onStop,
            hasInput = hasInput,
            enabled = enabled,
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * 现代化发送/停止按钮
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
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    var showContextMenu by remember { mutableStateOf(false) }
    
    // 确定当前图标文本（临时替换避免ClassLoader冲突）
    val currentIconText = when {
        isGenerating && !hasInput -> "⏹"  // 停止图标
        else -> "▲"  // 发送图标（向上箭头）
    }
    
    // 颜色动画
    val targetColor = when {
        !enabled -> JewelTheme.globalColors.text.disabled.copy(alpha = 0.5f)
        isGenerating && !hasInput -> Color(0xFFFF4444) // 红色停止按钮
        else -> Color(0xFF007AFF) // 蓝色发送按钮
    }
    
    val backgroundColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(200),
        label = "button color"
    )
    
    // 缩放动画
    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.9f
            isHovered && enabled && (hasInput || isGenerating) -> 1.1f
            else -> 1f
        },
        animationSpec = tween(100),
        label = "button scale"
    )
    
    // 阴影动画
    val shadowElevation by animateFloatAsState(
        targetValue = if (isHovered && enabled && (hasInput || isGenerating)) 4f else 0f,
        animationSpec = tween(200),
        label = "shadow elevation"
    )
    
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .scale(scale)
                .shadow(
                    elevation = shadowElevation.dp,
                    shape = CircleShape,
                    clip = false
                )
                .clip(CircleShape)
                .background(backgroundColor)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null, // 自定义动画，不使用默认波纹
                    enabled = enabled,
                    onClick = {
                        when {
                            isGenerating && !hasInput -> onStop()  // 停止生成
                            isGenerating && hasInput -> onSend()   // 添加到队列
                            hasInput -> onSend()                   // 直接发送
                        }
                    },
                    onLongClick = {
                        // 长按或右键显示菜单（仅在正在生成且有输入时）
                        if (isGenerating && hasInput && onInterruptAndSend != null) {
                            showContextMenu = true
                        }
                    }
                )
                .hoverable(interactionSource, enabled),
            contentAlignment = Alignment.Center
        ) {
            // 临时使用Text替代Icon避免ClassLoader冲突
            Text(
                text = currentIconText,
                style = JewelTheme.defaultTextStyle.copy(
                    color = Color.White,
                    fontSize = 12.sp
                )
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
                        Text("发送", style = JewelTheme.defaultTextStyle)
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
                            Text("打断并发送", style = JewelTheme.defaultTextStyle)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 图片选择按钮
 */
@Composable
fun ImagePickerButton(
    onImageSelected: (File) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    
    // 背景透明度动画
    val backgroundAlpha by animateFloatAsState(
        targetValue = when {
            !enabled -> 0.1f
            isHovered -> 0.15f
            else -> 0.05f
        },
        animationSpec = tween(200),
        label = "background alpha"
    )
    
    // 边框颜色动画
    val borderColor by animateColorAsState(
        targetValue = when {
            !enabled -> JewelTheme.globalColors.borders.disabled
            isHovered -> JewelTheme.globalColors.borders.focused
            else -> JewelTheme.globalColors.borders.normal
        },
        animationSpec = tween(200),
        label = "border color"
    )
    
    // 缩放动画
    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.9f
            isHovered && enabled -> 1.05f
            else -> 1f
        },
        animationSpec = tween(100),
        label = "scale"
    )
    
    Box(
        modifier = modifier
            .scale(scale)
            .clip(CircleShape)
            .background(
                JewelTheme.globalColors.borders.focused.copy(alpha = backgroundAlpha)
            )
            .border(
                1.dp,
                borderColor,
                CircleShape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled
            ) {
                showImagePicker(onImageSelected)
            }
            .hoverable(interactionSource, enabled),
        contentAlignment = Alignment.Center
    ) {
        // 临时使用Text替代Icon避免ClassLoader冲突
        Text(
            text = "📷",
            style = JewelTheme.defaultTextStyle.copy(
                color = if (enabled) 
                    JewelTheme.globalColors.text.normal 
                else 
                    JewelTheme.globalColors.text.disabled,
                fontSize = 12.sp
            )
        )
    }
}

/**
 * 显示图片选择器
 */
private fun showImagePicker(onImageSelected: (File) -> Unit) {
    try {
        val fileChooser = JFileChooser()
        fileChooser.dialogTitle = "选择图片"
        fileChooser.fileSelectionMode = JFileChooser.FILES_ONLY
        
        // 设置文件过滤器，只显示图片文件
        val imageFilter = FileNameExtensionFilter(
            "图片文件 (*.jpg, *.jpeg, *.png, *.gif, *.bmp, *.webp)",
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