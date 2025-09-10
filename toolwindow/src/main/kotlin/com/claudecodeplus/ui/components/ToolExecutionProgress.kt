package com.claudecodeplus.ui.jewel.components.tools

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

/**
 * 线性进度条组件
 * 支持确定进度和不确定进度两种模式
 */
@Composable
fun LinearProgressBar(
    progress: Float? = null, // null 表示不确定进度
    modifier: Modifier = Modifier,
    height: Dp = 4.dp,
    backgroundColor: Color = JewelTheme.globalColors.panelBackground.copy(alpha = 0.3f),
    progressColor: Color = Color(0xFF2196F3),
    animationDuration: Int = 1000
) {
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(backgroundColor)
    ) {
        if (progress != null) {
            // 确定进度
            DeterminateProgress(
                progress = progress.coerceIn(0f, 1f),
                progressColor = progressColor,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // 不确定进度
            IndeterminateProgress(
                progressColor = progressColor,
                animationDuration = animationDuration,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * 确定进度的显示
 */
@Composable
private fun DeterminateProgress(
    progress: Float,
    progressColor: Color,
    modifier: Modifier = Modifier
) {
    // 使用动画平滑过渡进度变化
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(
            durationMillis = 300,
            easing = FastOutSlowInEasing
        )
    )
    
    Box(
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth(animatedProgress)
            .background(progressColor)
    )
}

/**
 * 不确定进度的动画
 */
@Composable
private fun IndeterminateProgress(
    progressColor: Color,
    animationDuration: Int,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition()
    
    // 进度条位置动画
    val firstLineHead by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = animationDuration
                0f at 0 with FastOutSlowInEasing
                1f at animationDuration
            }
        )
    )
    
    val firstLineTail by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = animationDuration
                0f at 0 with FastOutSlowInEasing
                0f at animationDuration * 3 / 10 with FastOutSlowInEasing
                1f at animationDuration
            }
        )
    )
    
    Canvas(modifier = modifier) {
        val width = size.width
        
        // 绘制移动的进度条
        val headPos = width * firstLineHead
        val tailPos = width * firstLineTail
        
        drawRect(
            color = progressColor,
            topLeft = Offset(tailPos, 0f),
            size = Size(headPos - tailPos, size.height)
        )
    }
}

/**
 * 圆形进度条组件
 */
@Composable
fun CircularProgressBar(
    progress: Float? = null,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    strokeWidth: Dp = 4.dp,
    backgroundColor: Color = JewelTheme.globalColors.panelBackground.copy(alpha = 0.3f),
    progressColor: Color = Color(0xFF2196F3),
    showPercentage: Boolean = true
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // 背景圆环
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircularProgress(
                progress = 1f,
                color = backgroundColor,
                strokeWidth = strokeWidth
            )
        }
        
        // 进度圆环
        if (progress != null) {
            // 确定进度
            val animatedProgress by animateFloatAsState(
                targetValue = progress.coerceIn(0f, 1f),
                animationSpec = tween(
                    durationMillis = 300,
                    easing = FastOutSlowInEasing
                )
            )
            
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircularProgress(
                    progress = animatedProgress,
                    color = progressColor,
                    strokeWidth = strokeWidth
                )
            }
            
            // 百分比文字
            if (showPercentage) {
                Text(
                    text = "${(animatedProgress * 100).toInt()}%",
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = (size.value / 4).sp,
                        color = JewelTheme.globalColors.text.normal
                    )
                )
            }
        } else {
            // 不确定进度 - 旋转动画
            val infiniteTransition = rememberInfiniteTransition()
            val rotation by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 1000,
                        easing = LinearEasing
                    )
                )
            )
            
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationZ = rotation }
            ) {
                drawCircularProgress(
                    progress = 0.25f,
                    color = progressColor,
                    strokeWidth = strokeWidth,
                    startAngle = 0f
                )
            }
        }
    }
}

/**
 * 绘制圆形进度
 */
private fun DrawScope.drawCircularProgress(
    progress: Float,
    color: Color,
    strokeWidth: Dp,
    startAngle: Float = -90f
) {
    val stroke = Stroke(
        width = strokeWidth.toPx(),
        cap = StrokeCap.Round
    )
    
    val diameter = size.minDimension
    val radius = diameter / 2
    val topLeft = Offset(
        (size.width - diameter) / 2,
        (size.height - diameter) / 2
    )
    
    drawArc(
        color = color,
        startAngle = startAngle,
        sweepAngle = progress * 360f,
        useCenter = false,
        topLeft = topLeft,
        size = Size(diameter, diameter),
        style = stroke
    )
}

/**
 * 带标签的进度条
 */
@Composable
fun LabeledProgressBar(
    label: String,
    progress: Float? = null,
    modifier: Modifier = Modifier,
    showPercentage: Boolean = true
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 12.sp,
                    color = JewelTheme.globalColors.text.normal
                )
            )
            
            if (progress != null && showPercentage) {
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 11.sp,
                        color = JewelTheme.globalColors.text.normal.copy(alpha = 0.6f)
                    )
                )
            }
        }
        
        LinearProgressBar(
            progress = progress,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * 步骤进度指示器
 */
@Composable
fun StepProgressIndicator(
    totalSteps: Int,
    currentStep: Int,
    modifier: Modifier = Modifier,
    stepSize: Dp = 8.dp,
    spacing: Dp = 4.dp,
    completedColor: Color = Color(0xFF4CAF50),
    currentColor: Color = Color(0xFF2196F3),
    pendingColor: Color = JewelTheme.globalColors.panelBackground.copy(alpha = 0.3f)
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(totalSteps) { index ->
            val color = when {
                index < currentStep -> completedColor
                index == currentStep -> currentColor
                else -> pendingColor
            }
            
            Box(
                modifier = Modifier
                    .size(stepSize)
                    .clip(RoundedCornerShape(stepSize / 2))
                    .background(color)
            )
        }
    }
}

/**
 * Modifier 扩展，添加 graphicsLayer 支持
 */
private fun Modifier.graphicsLayer(
    block: GraphicsLayerScope.() -> Unit
): Modifier = this.then(
    object : Modifier.Element {
        override fun toString() = "GraphicsLayer"
    }
)

private interface GraphicsLayerScope {
    var rotationZ: Float
}