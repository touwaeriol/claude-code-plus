/*
 * PopupPositioning.kt
 * 
 * 通用弹窗定位工具类
 * 提供统一的弹窗位置计算和边界检测功能
 */

package com.claudecodeplus.ui.jewel.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 弹窗位置配置
 */
data class PopupPositionConfig(
    val preferredAlignment: PopupAlignment = PopupAlignment.ABOVE,
    val spacing: Int = 8, // dp
    val maxWidth: Int = 360, // dp  
    val maxHeight: Int = 320, // dp
    val enableBoundaryCheck: Boolean = true
)

/**
 * 弹窗对齐方式
 */
enum class PopupAlignment {
    ABOVE,      // 显示在目标上方
    BELOW,      // 显示在目标下方
    LEFT,       // 显示在目标左侧
    RIGHT,      // 显示在目标右侧
    AUTO        // 自动选择最佳位置
}

/**
 * 弹窗定位结果
 */
data class PopupPosition(
    val offset: IntOffset,
    val alignment: androidx.compose.ui.Alignment,
    val actualAlignment: PopupAlignment
)

/**
 * 弹窗定位工具类
 */
object PopupPositioning {
    
    /**
     * 计算按钮下方弹窗位置
     * 
     * @param buttonCoordinates 按钮的布局坐标
     * @param popupConfig 弹窗配置
     * @param density Density 对象
     * @return 弹窗位置
     */
    fun calculateButtonPopupPosition(
        buttonCoordinates: LayoutCoordinates?,
        popupConfig: PopupPositionConfig = PopupPositionConfig(),
        density: Density
    ): PopupPosition {
        if (buttonCoordinates == null) {
            return PopupPosition(
                offset = IntOffset.Zero,
                alignment = androidx.compose.ui.Alignment.Center,
                actualAlignment = PopupAlignment.BELOW
            )
        }
        
        val buttonPosition = buttonCoordinates.positionInRoot()
        val buttonSize = buttonCoordinates.size
        val spacingPx = with(density) { popupConfig.spacing.dp.toPx() }
        
        // 根据配置的对齐方式计算弹窗位置，实现水平居中对齐
        val popupWidth = with(density) { popupConfig.maxWidth.dp.toPx() }
        val popupHeight = with(density) { popupConfig.maxHeight.dp.toPx() }
        
        // 计算按钮中心点
        val buttonCenterX = buttonPosition.x + buttonSize.width / 2
        // 计算弹窗左边缘位置，使弹窗水平居中
        val popupX = (buttonCenterX - popupWidth / 2).toInt()
        
        val (x, y, actualAlignment) = when (popupConfig.preferredAlignment) {
            PopupAlignment.ABOVE -> {
                // 使用固定间距而不是估算的弹窗高度，避免计算不准
                val fixedOffset = with(density) { 200.dp.toPx() } // 固定 200dp 的上方间距
                Triple(
                    popupX,
                    (buttonPosition.y - fixedOffset).toInt(),
                    PopupAlignment.ABOVE
                )
            }
            PopupAlignment.BELOW -> {
                Triple(
                    popupX,
                    (buttonPosition.y + buttonSize.height + spacingPx).toInt(),
                    PopupAlignment.BELOW
                )
            }
            else -> {
                // 默认放在上方，使用固定间距
                val fixedOffset = with(density) { 200.dp.toPx() }
                Triple(
                    popupX,
                    (buttonPosition.y - fixedOffset).toInt(),
                    PopupAlignment.ABOVE
                )
            }
        }
        
        return PopupPosition(
            offset = IntOffset(x, y),
            alignment = androidx.compose.ui.Alignment.TopStart,
            actualAlignment = actualAlignment
        )
    }
    
    /**
     * 计算 @ 符号弹窗位置
     * 
     * @param textFieldCoordinates 文本框坐标
     * @param textFieldValue 文本框值
     * @param atPosition @ 符号位置
     * @param popupConfig 弹窗配置
     * @param density Density 对象
     * @return 弹窗位置
     */
    fun calculateAtSymbolPopupPosition(
        textFieldCoordinates: LayoutCoordinates?,
        textFieldValue: TextFieldValue,
        atPosition: Int,
        popupConfig: PopupPositionConfig = PopupPositionConfig(),
        density: Density
    ): PopupPosition {
        if (textFieldCoordinates == null) {
            return PopupPosition(
                offset = IntOffset.Zero,
                alignment = androidx.compose.ui.Alignment.Center,
                actualAlignment = PopupAlignment.ABOVE
            )
        }
        
        // 字符尺寸估算
        val charWidth = with(density) { 8.sp.toPx() }
        val lineHeight = with(density) { 20.sp.toPx() }
        val padding = with(density) { 16.dp.toPx() }
        val spacingPx = with(density) { popupConfig.spacing.dp.toPx() }
        val popupHeight = with(density) { popupConfig.maxHeight.dp.toPx() }
        
        // 计算 @ 符号在文本中的位置
        val textBeforeAt = textFieldValue.text.substring(0, atPosition)
        val linesBeforeAt = textBeforeAt.count { it == '\n' }
        val lastLineStart = textBeforeAt.lastIndexOf('\n') + 1
        val charPositionInLine = atPosition - lastLineStart
        
        // @ 符号的相对位置
        val atX = charPositionInLine * charWidth + padding
        val atY = linesBeforeAt * lineHeight + padding
        
        // 计算弹窗水平居中位置
        val popupWidth = with(density) { popupConfig.maxWidth.dp.toPx() }
        // 让弹窗以 @ 符号为中心水平居中
        val centeredX = (atX - popupWidth / 2).toInt()
        
        // 弹窗位置：@ 符号上方，水平居中，使用固定间距
        val x = centeredX
        val fixedOffset = with(density) { 200.dp.toPx() } // 使用固定 200dp 偏移
        val y = (atY - fixedOffset).toInt()
        
        return PopupPosition(
            offset = IntOffset(x, y),
            alignment = androidx.compose.ui.Alignment.TopStart,
            actualAlignment = PopupAlignment.ABOVE
        )
    }
    
    /**
     * 确保弹窗在屏幕边界内
     * 
     * @param position 原始位置
     * @param popupSize 弹窗尺寸
     * @param screenBounds 屏幕边界
     * @return 调整后的位置
     */
    fun ensureWithinBounds(
        position: PopupPosition,
        popupSize: androidx.compose.ui.unit.IntSize,
        screenBounds: Rect
    ): PopupPosition {
        val originalOffset = position.offset
        var adjustedX = originalOffset.x
        var adjustedY = originalOffset.y
        var newAlignment = position.actualAlignment
        
        // 检查右边界
        if (adjustedX + popupSize.width > screenBounds.right) {
            adjustedX = (screenBounds.right - popupSize.width).toInt()
        }
        
        // 检查左边界
        if (adjustedX < screenBounds.left) {
            adjustedX = screenBounds.left.toInt()
        }
        
        // 检查下边界
        if (adjustedY + popupSize.height > screenBounds.bottom) {
            adjustedY = (screenBounds.bottom - popupSize.height).toInt()
        }
        
        // 检查上边界
        if (adjustedY < screenBounds.top) {
            adjustedY = screenBounds.top.toInt()
            // 如果原本在上方显示，现在改为下方
            if (position.actualAlignment == PopupAlignment.ABOVE) {
                newAlignment = PopupAlignment.BELOW
            }
        }
        
        return position.copy(
            offset = IntOffset(adjustedX, adjustedY),
            actualAlignment = newAlignment
        )
    }
    
    /**
     * 自动选择最佳弹窗位置
     * 
     * @param targetCoordinates 目标元素坐标
     * @param popupConfig 弹窗配置
     * @param density Density 对象
     * @return 最佳弹窗位置
     */
    fun calculateOptimalPosition(
        targetCoordinates: LayoutCoordinates?,
        popupConfig: PopupPositionConfig = PopupPositionConfig(),
        density: Density
    ): PopupPosition {
        if (targetCoordinates == null) {
            return PopupPosition(
                offset = IntOffset.Zero,
                alignment = androidx.compose.ui.Alignment.Center,
                actualAlignment = PopupAlignment.AUTO
            )
        }
        
        // 根据配置选择对齐方式
        return when (popupConfig.preferredAlignment) {
            PopupAlignment.BELOW -> calculateButtonPopupPosition(targetCoordinates, popupConfig, density)
            PopupAlignment.ABOVE -> calculateButtonPopupPosition(targetCoordinates, popupConfig.copy(spacing = -popupConfig.spacing), density)
            else -> calculateButtonPopupPosition(targetCoordinates, popupConfig, density)
        }
    }
}

/**
 * Compose 扩展函数：简化弹窗位置计算
 */
@androidx.compose.runtime.Composable
fun rememberPopupPosition(
    targetCoordinates: LayoutCoordinates?,
    config: PopupPositionConfig = PopupPositionConfig()
): PopupPosition {
    val density = androidx.compose.ui.platform.LocalDensity.current
    return androidx.compose.runtime.remember(targetCoordinates, config) {
        PopupPositioning.calculateOptimalPosition(targetCoordinates, config, density)
    }
}