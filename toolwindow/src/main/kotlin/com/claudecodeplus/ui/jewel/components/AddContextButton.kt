/*
 * AddContextButton.kt
 * 
 * Add Context 按钮组件 - 触发上下文选择器
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.icons.AllIconsKeys

/**
 * Add Context 按钮组件
 * 
 * @param onClick 点击事件回调
 * @param enabled 是否启用
 * @param modifier 修饰符
 */
@Composable
fun AddContextButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    
    // 背景颜色动画
    val backgroundColor by animateColorAsState(
        targetValue = when {
            !enabled -> JewelTheme.globalColors.panelBackground
            isHovered -> JewelTheme.globalColors.borders.focused.copy(alpha = 0.1f)
            else -> JewelTheme.globalColors.panelBackground
        },
        animationSpec = tween(200),
        label = "background color"
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
            isPressed -> 0.95f
            isHovered && enabled -> 1.02f
            else -> 1f
        },
        animationSpec = tween(100),
        label = "scale"
    )
    
    Box(
        modifier = modifier
            .height(20.dp)
            .scale(scale)
            .clip(RoundedCornerShape(4.dp))
            .background(backgroundColor)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(4.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled
            ) { onClick() }
            .hoverable(interactionSource, enabled)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                key = AllIconsKeys.General.Add,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = if (enabled) 
                    JewelTheme.globalColors.text.normal 
                else 
                    JewelTheme.globalColors.text.disabled
            )
            Text(
                text = "Add Context",
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 11.sp,
                    color = if (enabled) 
                        JewelTheme.globalColors.text.normal 
                    else 
                        JewelTheme.globalColors.text.disabled
                )
            )
        }
    }
} 