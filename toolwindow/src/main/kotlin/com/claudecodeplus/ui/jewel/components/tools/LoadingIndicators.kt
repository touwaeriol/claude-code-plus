package com.claudecodeplus.ui.jewel.components.tools

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import kotlinx.coroutines.delay

/**
 * 跳动的三个点动画组件
 * 用于表示正在处理或加载中的状态
 */
@Composable
fun JumpingDots(
    modifier: Modifier = Modifier,
    dotSize: Dp = 6.dp,
    dotSpacing: Dp = 4.dp,
    jumpHeight: Dp = 4.dp,
    color: Color = JewelTheme.globalColors.text.normal.copy(alpha = 0.6f),
    animationDuration: Int = 600
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(dotSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            JumpingDot(
                size = dotSize,
                jumpHeight = jumpHeight,
                color = color,
                animationDelay = index * (animationDuration / 3),
                animationDuration = animationDuration
            )
        }
    }
}

/**
 * 单个跳动的点
 */
@Composable
private fun JumpingDot(
    size: Dp,
    jumpHeight: Dp,
    color: Color,
    animationDelay: Int,
    animationDuration: Int
) {
    val infiniteTransition = rememberInfiniteTransition()
    
    val offsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = animationDuration
                0f at 0 with LinearEasing
                -jumpHeight.value at animationDuration / 2 with FastOutSlowInEasing
                0f at animationDuration with LinearEasing
            },
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(animationDelay)
        )
    )
    
    Box(
        modifier = Modifier
            .size(size)
            .offset(y = offsetY.dp)
            .clip(CircleShape)
            .background(color)
    )
}

/**
 * 脉冲效果的点动画
 * 用于表示等待或挂起状态
 */
@Composable
fun PulsingDot(
    modifier: Modifier = Modifier,
    size: Dp = 8.dp,
    color: Color = JewelTheme.globalColors.text.normal.copy(alpha = 0.6f),
    minAlpha: Float = 0.3f,
    maxAlpha: Float = 1f,
    animationDuration: Int = 1000
) {
    val infiniteTransition = rememberInfiniteTransition()
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = minAlpha,
        targetValue = maxAlpha,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = animationDuration,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = animationDuration,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .alpha(alpha)
            .clip(CircleShape)
            .background(color)
    )
}

/**
 * 旋转的圆形进度指示器
 * 用于表示正在处理的状态
 */
@Composable
fun SpinningProgress(
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    color: Color = JewelTheme.globalColors.text.normal.copy(alpha = 0.6f),
    strokeWidth: Dp = 2.dp,
    animationDuration: Int = 800
) {
    val infiniteTransition = rememberInfiniteTransition()
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = animationDuration,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        )
    )
    
    Box(
        modifier = modifier
            .size(size)
            .rotate(rotation),
        contentAlignment = Alignment.Center
    ) {
        // 使用简单的圆环表示进度
        CircularProgressIndicator(
            size = size,
            strokeWidth = strokeWidth,
            color = color
        )
    }
}

/**
 * 简单的圆形进度指示器
 */
@Composable
private fun CircularProgressIndicator(
    size: Dp,
    strokeWidth: Dp,
    color: Color
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.Transparent)
    ) {
        // 外圈
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(color.copy(alpha = 0.2f))
        )
        
        // 内圈（创建环形效果）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(strokeWidth)
                .clip(CircleShape)
                .background(JewelTheme.globalColors.panelBackground)
        )
        
        // 旋转的部分（使用渐变模拟）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
        ) {
            // 这里简化处理，实际可以使用 Canvas 绘制更精确的弧形
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .fillMaxHeight()
                    .background(color)
            )
        }
    }
}

/**
 * 成功勾的淡入动画
 */
@Composable
fun SuccessCheckmark(
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    color: Color = Color(0xFF4CAF50),
    animationDuration: Int = 300
) {
    var visible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        visible = true
    }
    
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = animationDuration)
    )
    
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.5f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )
    
    Box(
        modifier = modifier
            .size(size)
            .alpha(alpha)
            .scale(scale),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            key = AllIconsKeys.Actions.Commit,
            contentDescription = "Success",
            tint = color,
            modifier = Modifier.size(size)
        )
    }
}

/**
 * 错误叉的淡入动画
 */
@Composable
fun ErrorCross(
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    color: Color = Color(0xFFFF6B6B),
    animationDuration: Int = 300
) {
    var visible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        visible = true
    }
    
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = animationDuration)
    )
    
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.5f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )
    
    Box(
        modifier = modifier
            .size(size)
            .alpha(alpha)
            .scale(scale),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            key = AllIconsKeys.Actions.Close,
            contentDescription = "Error",
            tint = color,
            modifier = Modifier.size(size)
        )
    }
}

/**
 * 打字指示器动画
 * 用于表示AI正在生成响应
 */
@Composable
fun TypingIndicator(
    modifier: Modifier = Modifier,
    dotSize: Dp = 4.dp,
    dotSpacing: Dp = 3.dp,
    color: Color = JewelTheme.globalColors.text.normal.copy(alpha = 0.6f)
) {
    var visibleDots by remember { mutableStateOf(0) }
    
    LaunchedEffect(Unit) {
        while (true) {
            delay(300)
            visibleDots = (visibleDots + 1) % 4
        }
    }
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(dotSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .alpha(if (index < visibleDots) 1f else 0f)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

/**
 * 工具执行状态指示器
 * 根据状态显示不同的动画
 */
@Composable
fun ToolStatusIndicator(
    status: ToolExecutionStatus,
    modifier: Modifier = Modifier,
    size: Dp = 16.dp
) {
    when (status) {
        ToolExecutionStatus.PENDING -> {
            PulsingDot(
                modifier = modifier,
                size = size * 0.5f,
                color = JewelTheme.globalColors.text.normal.copy(alpha = 0.4f)
            )
        }
        ToolExecutionStatus.RUNNING -> {
            JumpingDots(
                modifier = modifier,
                dotSize = size * 0.3f,
                jumpHeight = size * 0.25f
            )
        }
        ToolExecutionStatus.SUCCESS -> {
            SuccessCheckmark(
                modifier = modifier,
                size = size
            )
        }
        ToolExecutionStatus.ERROR -> {
            ErrorCross(
                modifier = modifier,
                size = size
            )
        }
    }
}

/**
 * 工具执行状态枚举
 */
enum class ToolExecutionStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    ERROR
}