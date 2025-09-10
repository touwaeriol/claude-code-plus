/*
 * TextPositionUtils.kt
 * 
 * 文本定位工具类 - 提供精确的字符定位和弹窗定位计算
 * 基于 Compose TextLayoutResult 和 LayoutCoordinates 实现
 */

package com.claudecodeplus.ui.jewel.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

/**
 * 文本定位工具类
 * 
 * 提供基于 TextLayoutResult 的精确字符定位功能
 * 用于实现文本输入框中字符级别的弹窗定位
 */
object TextPositionUtils {
    
    /**
     * 计算字符在屏幕上的绝对位置
     * 
     * @param textLayoutResult 文本布局结果，包含字符的几何信息
     * @param characterPosition 字符在文本中的位置索引
     * @param inputFieldCoordinates 输入框的布局坐标（可选）
     * @return 字符在输入框中的相对位置，如果计算失败返回默认位置
     */
    fun calculateCharacterPosition(
        textLayoutResult: TextLayoutResult?,
        characterPosition: Int,
        inputFieldCoordinates: LayoutCoordinates? = null
    ): Offset {
        return textLayoutResult?.let { layoutResult ->
            try {
                // 使用 getBoundingBox 获取字符的精确边界框
                val charRect = layoutResult.getBoundingBox(characterPosition)
                
                // 返回字符的左上角位置（相对于文本内容）
                Offset(
                    x = charRect.left,
                    y = charRect.top
                )
            } catch (e: Exception) {
                // getBoundingBox 可能在以下情况失败：
                // 1. characterPosition 超出文本范围
                // 2. 文本还未完成布局
                // 3. TextLayoutResult 状态异常
                getDefaultCharacterPosition()
            }
        } ?: getDefaultCharacterPosition()
    }
    
    /**
     * 计算字符的屏幕绝对位置
     * 
     * 将文本相对坐标转换为屏幕坐标
     * 
     * @param textLayoutResult 文本布局结果
     * @param characterPosition 字符位置
     * @param inputFieldCoordinates 输入框坐标
     * @param density 密度对象，用于 dp 转换
     * @return 字符在屏幕上的绝对坐标
     */
    fun calculateAbsoluteCharacterPosition(
        textLayoutResult: TextLayoutResult?,
        characterPosition: Int,
        inputFieldCoordinates: LayoutCoordinates,
        density: Density
    ): Offset {
        // 获取字符在文本中的相对位置
        val relativePosition = calculateCharacterPosition(
            textLayoutResult, 
            characterPosition
        )
        
        // 获取输入框在屏幕上的位置
        val inputFieldScreenPosition = inputFieldCoordinates.positionInRoot()
        
        // 转换为屏幕绝对坐标
        return Offset(
            x = inputFieldScreenPosition.x + relativePosition.x,
            y = inputFieldScreenPosition.y + relativePosition.y
        )
    }
    
    /**
     * 计算弹窗在字符上方的位置
     * 
     * 专门用于计算弹窗显示在指定字符上方的位置
     * 
     * @param textLayoutResult 文本布局结果
     * @param characterPosition 字符位置
     * @param popupWidth 弹窗宽度（像素）
     * @param popupHeight 弹窗高度（像素）
     * @param spacing 弹窗与字符间的间距（dp）
     * @param density 密度对象
     * @return 弹窗相对于输入框的位置
     */
    fun calculatePopupAboveCharacter(
        textLayoutResult: TextLayoutResult?,
        characterPosition: Int,
        popupWidth: Int = 360,
        popupHeight: Int = 320,
        spacing: Float = 4f,
        density: Density
    ): Offset {
        val charPosition = calculateCharacterPosition(textLayoutResult, characterPosition)
        val spacingPx = with(density) { spacing.dp.toPx() }
        
        return Offset(
            // 水平居中：字符中心 - 弹窗宽度的一半
            x = charPosition.x - (popupWidth / 2f),
            // 垂直上方：字符顶部 - 弹窗高度 - 间距
            y = charPosition.y - popupHeight - spacingPx
        )
    }
    
    /**
     * 获取默认字符位置
     * 
     * 当无法计算精确位置时使用的回退位置
     */
    private fun getDefaultCharacterPosition(): Offset {
        return Offset(
            x = 20f,  // 输入框左侧 20px
            y = 10f   // 输入框顶部 10px
        )
    }
    
    /**
     * 验证字符位置是否有效
     * 
     * @param characterPosition 字符位置
     * @param textLength 文本总长度
     * @return 是否为有效位置
     */
    fun isValidCharacterPosition(characterPosition: Int, textLength: Int): Boolean {
        return characterPosition >= 0 && characterPosition < textLength
    }
    
    /**
     * 安全的字符位置计算
     * 
     * 在计算前先验证位置有效性
     * 
     * @param textLayoutResult 文本布局结果
     * @param characterPosition 字符位置
     * @param textLength 文本长度
     * @return 字符位置或默认位置
     */
    fun safeCalculateCharacterPosition(
        textLayoutResult: TextLayoutResult?,
        characterPosition: Int,
        textLength: Int
    ): Offset {
        return if (isValidCharacterPosition(characterPosition, textLength)) {
            calculateCharacterPosition(textLayoutResult, characterPosition)
        } else {
            getDefaultCharacterPosition()
        }
    }
}

/**
 * 文本定位配置
 * 
 * 用于配置不同场景下的定位参数
 */
data class TextPositionConfig(
    val spacing: Float = 4f,        // 间距（dp）
    val horizontalAlignment: HorizontalAlignment = HorizontalAlignment.CENTER,
    val verticalAlignment: VerticalAlignment = VerticalAlignment.ABOVE,
    val fallbackOffset: Offset = Offset(20f, 10f)
)

enum class HorizontalAlignment {
    LEFT,       // 左对齐
    CENTER,     // 居中对齐
    RIGHT       // 右对齐
}

enum class VerticalAlignment {
    ABOVE,      // 显示在上方
    BELOW,      // 显示在下方
    CENTER      // 垂直居中
}

/**
 * TextLayoutResult 扩展函数
 */
fun TextLayoutResult.getCharacterBounds(position: Int): androidx.compose.ui.geometry.Rect? {
    return try {
        getBoundingBox(position)
    } catch (e: Exception) {
        null
    }
}

/**
 * 字符位置计算的扩展函数
 */
fun TextLayoutResult.calculateCharacterOffset(position: Int): Offset {
    return TextPositionUtils.calculateCharacterPosition(this, position)
}