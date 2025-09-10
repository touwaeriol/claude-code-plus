package com.claudecodeplus.ui.jewel.components.tools

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text

/**
 * 现代化工具状态指示器组件
 * 使用图标化设计，提供清晰的视觉反馈
 */
@Composable
fun ModernStatusIndicator(
    status: ToolExecutionStatus,
    size: Dp = 12.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        when (status) {
            ToolExecutionStatus.SUCCESS -> {
                // 绿色对勾
                Icon(
                    key = AllIconsKeys.Actions.Checked,
                    contentDescription = "成功",
                    tint = Color(0xFF4CAF50), // Material Green 500
                    modifier = Modifier.size(size)
                )
            }
            
            ToolExecutionStatus.ERROR -> {
                // 红色叉号
                Icon(
                    key = AllIconsKeys.Actions.Close,
                    contentDescription = "失败",
                    tint = Color(0xFFF44336), // Material Red 500
                    modifier = Modifier.size(size)
                )
            }
            
            ToolExecutionStatus.RUNNING -> {
                // 蓝色脉冲点
                PulsingDot(
                    color = Color(0xFF2196F3), // Material Blue 500
                    size = size * 0.7f
                )
            }
            
            ToolExecutionStatus.PENDING -> {
                // 灰色等待点
                Box(
                    modifier = Modifier
                        .size(size * 0.6f)
                        .clip(CircleShape)
                        .background(Color(0xFF9E9E9E)) // Material Grey 500
                )
            }
        }
    }
}

/**
 * 脉冲点动画组件
 */
@Composable
private fun PulsingDot(
    color: Color,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulsing")
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    Box(
        modifier = modifier
            .size(size * scale)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}

/**
 * 文本状态指示器（备用方案）
 */
@Composable
fun TextStatusIndicator(
    status: ToolExecutionStatus,
    modifier: Modifier = Modifier
) {
    val (text, color) = when (status) {
        ToolExecutionStatus.SUCCESS -> "✓" to Color(0xFF4CAF50)
        ToolExecutionStatus.ERROR -> "✗" to Color(0xFFF44336)
        ToolExecutionStatus.RUNNING -> "●" to Color(0xFF2196F3)
        ToolExecutionStatus.PENDING -> "○" to Color(0xFF9E9E9E)
    }
    
    Text(
        text = text,
        color = color,
        style = JewelTheme.defaultTextStyle.copy(fontSize = 12.sp),
        modifier = modifier
    )
}

/**
 * 线条状态指示器（备用方案）
 */
@Composable
fun LineStatusIndicator(
    status: ToolExecutionStatus,
    width: Dp = 16.dp,
    height: Dp = 2.dp,
    modifier: Modifier = Modifier
) {
    val color = when (status) {
        ToolExecutionStatus.SUCCESS -> Color(0xFF4CAF50)
        ToolExecutionStatus.ERROR -> Color(0xFFF44336)
        ToolExecutionStatus.RUNNING -> Color(0xFF2196F3)
        ToolExecutionStatus.PENDING -> Color(0xFF9E9E9E)
    }
    
    Box(
        modifier = modifier
            .size(width, height)
            .background(color, shape = CircleShape)
    )
}