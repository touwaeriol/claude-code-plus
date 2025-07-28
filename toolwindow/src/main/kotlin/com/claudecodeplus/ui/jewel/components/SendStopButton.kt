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
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import java.io.File

/**
 * 发送/停止按钮组合组件
 * 包含发送按钮和图片选择按钮
 * 
 * @param isGenerating 是否正在生成
 * @param onSend 发送消息回调
 * @param onStop 停止生成回调
 * @param onImageSelected 图片选择回调
 * @param hasInput 是否有输入内容
 * @param enabled 是否启用
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
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
 */
@Composable
fun SendStopButton(
    isGenerating: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    hasInput: Boolean = true,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    
    // 颜色动画
    val targetColor = when {
        !enabled || (!hasInput && !isGenerating) -> JewelTheme.globalColors.text.disabled.copy(alpha = 0.5f)
        isGenerating -> Color(0xFFFF4444) // 红色停止按钮
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
    
    Box(
        modifier = modifier
            .scale(scale)
            .shadow(
                elevation = shadowElevation.dp,
                shape = CircleShape,
                clip = false
            )
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null, // 自定义动画，不使用默认波纹
                enabled = enabled && (hasInput || isGenerating)
            ) {
                if (isGenerating) {
                    onStop()
                } else {
                    onSend()
                }
            }
            .hoverable(interactionSource, enabled && (hasInput || isGenerating)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            key = if (isGenerating) AllIconsKeys.Actions.Suspend else AllIconsKeys.General.ArrowUp,
            contentDescription = if (isGenerating) "Stop" else "Send",
            modifier = Modifier.size(14.dp),
            tint = Color.White
        )
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
        Icon(
            key = AllIconsKeys.FileTypes.Image,
            contentDescription = "Select image",
            modifier = Modifier.size(12.dp),
            tint = if (enabled) 
                JewelTheme.globalColors.text.normal 
            else 
                JewelTheme.globalColors.text.disabled
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