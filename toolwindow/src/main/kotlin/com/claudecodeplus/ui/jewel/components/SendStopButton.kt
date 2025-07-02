/*
 * SendStopButton.kt
 * 
 * 发送/停止按钮组件 - 现代化设计风格
 */

package com.claudecodeplus.ui.jewel.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
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
    val backgroundColor = when {
        !enabled || (!hasInput && !isGenerating) -> JewelTheme.globalColors.text.disabled
        isGenerating -> Color(0xFFFF4444) // 红色停止按钮
        else -> Color(0xFF007AFF) // 蓝色发送按钮
    }
    
    val contentColor = Color.White
    
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(enabled = enabled && (hasInput || isGenerating)) {
                if (isGenerating) {
                    onStop()
                } else {
                    onSend()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isGenerating) "⏹" else "↑",
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 12.sp,
                color = contentColor
            )
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
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(
                if (enabled) 
                    JewelTheme.globalColors.panelBackground 
                else 
                    JewelTheme.globalColors.text.disabled.copy(alpha = 0.3f)
            )
            .border(
                1.dp,
                if (enabled) 
                    JewelTheme.globalColors.borders.normal 
                else 
                    JewelTheme.globalColors.borders.disabled,
                CircleShape
            )
            .clickable(enabled = enabled) {
                showImagePicker(onImageSelected)
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "🖼",
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 10.sp,
                color = if (enabled) 
                    JewelTheme.globalColors.text.normal 
                else 
                    JewelTheme.globalColors.text.disabled
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
        println("图片选择错误: ${e.message}")
        e.printStackTrace()
    }
} 