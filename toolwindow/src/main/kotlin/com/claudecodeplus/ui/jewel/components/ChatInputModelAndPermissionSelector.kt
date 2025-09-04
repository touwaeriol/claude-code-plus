/*
 * ChatInputModelAndPermissionSelector.kt
 * 
 * 聊天输入区域的模型选择器组件 - 使用Jewel标准组件
 */

package com.claudecodeplus.ui.jewel.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay
import com.claudecodeplus.ui.models.AiModel
import com.claudecodeplus.ui.models.PermissionMode
import com.claudecodeplus.ui.jewel.components.utils.ComboBoxChevronIcon
import com.claudecodeplus.ui.jewel.components.utils.IconUtils
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.component.styling.ComboBoxStyle

/**
 * 模型选择器组件 - 使用自定义下拉组件，完全避免Jewel ComboBox的图标加载问题
 */
@OptIn(ExperimentalJewelApi::class)
@Composable
fun ChatInputModelSelector(
    currentModel: AiModel,
    onModelChange: (AiModel) -> Unit,
    enabled: Boolean = true,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val models = listOf(AiModel.DEFAULT, AiModel.OPUS, AiModel.SONNET, AiModel.OPUS_PLAN, AiModel.OPUS_4)
    val currentIndex = models.indexOf(currentModel).takeIf { it >= 0 } ?: 0
    
    // 自定义下拉组件，完全避免 Jewel ComboBox 的图标加载问题
    var expanded by remember { mutableStateOf(false) }
    
    Box(modifier = modifier) {
        // 主按钮区域
        Row(
            modifier = Modifier
                .widthIn(
                    min = if (compact) 70.dp else 85.dp, 
                    max = if (compact) 100.dp else 120.dp  // 进一步减少模型选择器宽度，为权限选择器让出空间
                )
                .background(
                    JewelTheme.globalColors.panelBackground,
                    RoundedCornerShape(4.dp)
                )
                .border(
                    1.dp,
                    if (expanded) JewelTheme.globalColors.borders.focused 
                    else JewelTheme.globalColors.borders.normal,
                    RoundedCornerShape(4.dp)
                )
                .clickable(enabled = enabled) { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (compact) models[currentIndex].shortName else models[currentIndex].displayName,
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = if (compact) 11.sp else 12.sp,
                    color = if (enabled) JewelTheme.globalColors.text.normal 
                           else JewelTheme.globalColors.text.disabled
                ),
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            
            // 使用我们的安全图标
            ComboBoxChevronIcon(enabled = enabled)
        }
        
        // 下拉面板
        if (expanded) {
            Popup(
                alignment = Alignment.TopStart,
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true)
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(
                            min = if (compact) 70.dp else 85.dp, 
                            max = if (compact) 100.dp else 120.dp  // 与主按钮保持一致
                        )
                        .background(
                            JewelTheme.globalColors.panelBackground,
                            RoundedCornerShape(4.dp)
                        )
                        .border(
                            1.dp,
                            JewelTheme.globalColors.borders.focused,
                            RoundedCornerShape(4.dp)
                        )
                        .shadow(4.dp, RoundedCornerShape(4.dp))
                ) {
                    Column {
                        models.forEachIndexed { index, model ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (index == currentIndex) 
                                            JewelTheme.globalColors.panelBackground.copy(alpha = 0.9f)
                                        else Color.Transparent
                                    )
                                    .clickable { 
                                        onModelChange(model)
                                        expanded = false 
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (compact) model.shortName else model.displayName,
                                    style = JewelTheme.defaultTextStyle.copy(
                                        fontSize = if (compact) 11.sp else 12.sp,
                                        color = if (index == currentIndex) 
                                            JewelTheme.globalColors.text.selected 
                                        else 
                                            JewelTheme.globalColors.text.normal
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 权限模式选择器组件 - 使用自定义下拉组件，完全避免Jewel ComboBox的图标加载问题
 */
@OptIn(ExperimentalJewelApi::class)
@Composable
fun ChatInputPermissionSelector(
    currentPermissionMode: PermissionMode = PermissionMode.BYPASS,
    onPermissionModeChange: (PermissionMode) -> Unit,
    enabled: Boolean = true,
    compact: Boolean = false,
    iconOnly: Boolean = false,
    modifier: Modifier = Modifier
) {
    val permissionModes = listOf(
        PermissionMode.DEFAULT,
        PermissionMode.ACCEPT,
        PermissionMode.BYPASS,
        PermissionMode.PLAN
    )
    val currentIndex = permissionModes.indexOf(currentPermissionMode).takeIf { it >= 0 } ?: 0
    
    if (iconOnly) {
        // 图标模式：使用简单的文本按钮显示权限模式图标
        Box(
            modifier = modifier
                .size(32.dp)
                .background(
                    color = if (enabled) JewelTheme.globalColors.borders.normal else JewelTheme.globalColors.borders.disabled,
                    shape = RoundedCornerShape(6.dp)
                )
                .clickable(enabled = enabled) {
                    // 循环切换到下一个权限模式
                    val nextIndex = (currentIndex + 1) % permissionModes.size
                    onPermissionModeChange(permissionModes[nextIndex])
                }
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = currentPermissionMode.icon,
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 14.sp,
                    color = if (enabled) JewelTheme.globalColors.text.normal else JewelTheme.globalColors.text.disabled
                )
            )
        }
    } else {
        // 自定义下拉组件，完全避免 Jewel ComboBox 的图标加载问题
        var expanded by remember { mutableStateOf(false) }
        
        Box(modifier = modifier) {
            // 主按钮区域
            Row(
                modifier = Modifier
                    .widthIn(
                        min = if (compact) 100.dp else 160.dp,  // 增加最小宽度确保 "Bypass" 水平显示
                        max = if (compact) 160.dp else 240.dp  // 相应增加最大宽度
                    )
                    .background(
                        JewelTheme.globalColors.panelBackground,
                        RoundedCornerShape(4.dp)
                    )
                    .border(
                        1.dp,
                        if (expanded) JewelTheme.globalColors.borders.focused 
                        else JewelTheme.globalColors.borders.normal,
                        RoundedCornerShape(4.dp)
                    )
                    .clickable(enabled = enabled) { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (compact) permissionModes[currentIndex].shortName else permissionModes[currentIndex].displayName,
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = if (compact) 11.sp else 12.sp,
                        color = if (enabled) JewelTheme.globalColors.text.normal 
                               else JewelTheme.globalColors.text.disabled
                    ),
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                
                // 使用我们的安全图标
                ComboBoxChevronIcon(enabled = enabled)
            }
            
            // 下拉面板
            if (expanded) {
                Popup(
                    alignment = Alignment.TopStart,
                    onDismissRequest = { expanded = false },
                    properties = PopupProperties(focusable = true)
                ) {
                    Box(
                        modifier = Modifier
                            .widthIn(
                                min = if (compact) 100.dp else 160.dp,  // 与主按钮保持一致
                                max = if (compact) 160.dp else 240.dp  // 与主按钮保持一致
                            )
                            .background(
                                JewelTheme.globalColors.panelBackground,
                                RoundedCornerShape(4.dp)
                            )
                            .border(
                                1.dp,
                                JewelTheme.globalColors.borders.focused,
                                RoundedCornerShape(4.dp)
                            )
                            .shadow(4.dp, RoundedCornerShape(4.dp))
                    ) {
                        Column {
                            permissionModes.forEachIndexed { index, mode ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (index == currentIndex) 
                                                JewelTheme.globalColors.panelBackground.copy(alpha = 0.9f)
                                            else Color.Transparent
                                        )
                                        .clickable { 
                                            onPermissionModeChange(mode)
                                            expanded = false 
                                        }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (compact) {
                                        // 紧凑模式：显示图标+简称
                                        Text(
                                            text = mode.icon,
                                            style = JewelTheme.defaultTextStyle.copy(fontSize = 12.sp),
                                            modifier = Modifier.padding(end = 4.dp)
                                        )
                                        Text(
                                            text = mode.shortName,
                                            style = JewelTheme.defaultTextStyle.copy(
                                                fontSize = 11.sp,
                                                color = if (index == currentIndex) 
                                                    JewelTheme.globalColors.text.selected 
                                                else 
                                                    JewelTheme.globalColors.text.normal
                                            )
                                        )
                                    } else {
                                        // 标准模式：显示权限模式名称
                                        Text(
                                            text = mode.displayName,
                                            style = JewelTheme.defaultTextStyle.copy(
                                                fontSize = 12.sp,
                                                color = if (index == currentIndex) 
                                                    JewelTheme.globalColors.text.selected 
                                                else 
                                                    JewelTheme.globalColors.text.normal
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 现代化的权限开关组件 - 自定义Toggle样式，适配暗色主题
 */
@Composable
fun SkipPermissionsToggle(
    checked: Boolean = false,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    compact: Boolean = false,
    iconOnly: Boolean = false,
    modifier: Modifier = Modifier
) {
    val animatedColor by animateColorAsState(
        targetValue = if (checked) {
            // 激活状态：使用主题强调色
            if (enabled) 
                JewelTheme.globalColors.borders.focused
            else 
                JewelTheme.globalColors.borders.disabled
        } else {
            // 非激活状态：使用更柔和的背景色
            if (enabled)
                JewelTheme.globalColors.borders.normal.copy(alpha = 0.3f)
            else
                JewelTheme.globalColors.borders.disabled.copy(alpha = 0.2f)
        },
        animationSpec = tween(200),
        label = "toggle background color"
    )
    
    val thumbColor by animateColorAsState(
        targetValue = if (checked) {
            // 激活状态的滑块颜色
            JewelTheme.globalColors.panelBackground
        } else {
            // 非激活状态的滑块颜色  
            if (enabled)
                JewelTheme.globalColors.text.normal.copy(alpha = 0.8f)
            else
                JewelTheme.globalColors.text.disabled
        },
        animationSpec = tween(200),
        label = "toggle thumb color"
    )
    
    val animatedThumbOffset by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(200),
        label = "toggle thumb position"
    )
    
    if (iconOnly) {
        // 仅开关模式：紧凑的开关按钮
        Box(
            modifier = modifier
                .size(width = 32.dp, height = 18.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(animatedColor)
                .clickable(enabled = enabled) {
                    println("[SkipPermissionsToggle] Toggle 点击 (图标模式) - checked: $checked -> ${!checked}")
                    onCheckedChange(!checked)
                }
                .padding(2.dp)
        ) {
            // 滑动的圆形指示器
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .align(Alignment.CenterStart)
                    .offset(x = (14.dp * animatedThumbOffset))
                    .clip(CircleShape)
                    .background(thumbColor)
            )
        }
    } else {
        // 使用智能布局：先测量标签宽度，如果空间不够就隐藏标签
        val density = LocalDensity.current
        
        SubcomposeLayout(modifier = modifier) { constraints ->
            // 1. 先测量开关本体的尺寸（固定38dp宽度）
            val switchWidth = with(density) { 38.dp.toPx() }.toInt()
            val switchHeight = with(density) { 20.dp.toPx() }.toInt()
            
            // 2. 测量标签文本的尺寸
            val labelPlaceable = subcompose("label") {
                Text(
                    text = "bypass",
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = if (compact) 10.sp else 11.sp,
                        color = if (enabled) 
                            JewelTheme.globalColors.text.normal 
                        else 
                            JewelTheme.globalColors.text.disabled
                    )
                )
            }.firstOrNull()?.measure(constraints)
            
            val spacing = with(density) { (if (compact) 6.dp else 8.dp).toPx() }.toInt()
            val totalRequiredWidth = switchWidth + spacing + (labelPlaceable?.width ?: 0)
            
            // 3. 决定是否显示标签
            val showLabel = totalRequiredWidth <= constraints.maxWidth
            
            // 4. 创建实际布局
            val finalPlaceables = subcompose("content") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (showLabel) 
                        Arrangement.spacedBy(if (compact) 6.dp else 8.dp) 
                    else 
                        Arrangement.Center,
                    modifier = Modifier
                ) {
                    // 开关本体
                    Box(
                        modifier = Modifier
                            .size(width = 38.dp, height = 20.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(animatedColor)
                            .clickable(enabled = enabled) {
                                println("[SkipPermissionsToggle] Toggle 点击 - checked: $checked -> ${!checked}")
                                onCheckedChange(!checked)
                            }
                            .padding(2.dp)
                    ) {
                        // 滑动的圆形指示器
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .align(Alignment.CenterStart)
                                .offset(x = (16.dp * animatedThumbOffset))
                                .clip(CircleShape)
                                .background(thumbColor)
                        )
                    }
                    
                    // 标签文本 - 只有在有足够空间时才显示
                    if (showLabel) {
                        Text(
                            text = "bypass",
                            style = JewelTheme.defaultTextStyle.copy(
                                fontSize = if (compact) 10.sp else 11.sp,
                                color = if (enabled) 
                                    JewelTheme.globalColors.text.normal 
                                else 
                                    JewelTheme.globalColors.text.disabled
                            )
                        )
                    }
                }
            }.map { it.measure(constraints) }
            
            // 5. 布局
            val layoutWidth = if (showLabel) totalRequiredWidth else switchWidth
            val layoutHeight = maxOf(switchHeight, labelPlaceable?.height ?: switchHeight)
            
            layout(minOf(layoutWidth, constraints.maxWidth), layoutHeight) {
                finalPlaceables.forEach { placeable ->
                    placeable.place(0, 0)
                }
            }
        }
    }
}

/**
 * 保持向后兼容性的别名函数
 */
@Composable
fun SkipPermissionsCheckbox(
    checked: Boolean = false,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    compact: Boolean = false,
    iconOnly: Boolean = false,
    modifier: Modifier = Modifier
) {
    SkipPermissionsToggle(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        compact = compact,
        iconOnly = iconOnly,
        modifier = modifier
    )
}

/**
 * 自动清理上下文复选框
 */
@Composable
fun AutoCleanupContextsCheckbox(
    checked: Boolean = false,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    compact: Boolean = false,
    iconOnly: Boolean = false,
    modifier: Modifier = Modifier
) {
    val animatedColor by animateColorAsState(
        targetValue = if (checked) {
            // 激活状态：使用主题强调色
            if (enabled) 
                JewelTheme.globalColors.borders.focused
            else 
                JewelTheme.globalColors.borders.disabled
        } else {
            // 非激活状态：使用更柔和的背景色
            if (enabled)
                JewelTheme.globalColors.borders.normal.copy(alpha = 0.3f)
            else
                JewelTheme.globalColors.borders.disabled.copy(alpha = 0.2f)
        },
        animationSpec = tween(200)
    )

    val textColor = if (enabled) {
        JewelTheme.globalColors.text.normal
    } else {
        JewelTheme.globalColors.text.disabled
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .background(animatedColor.copy(alpha = 0.1f))
            .padding(horizontal = if (compact) 4.dp else 6.dp, vertical = if (compact) 2.dp else 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 复选框指示器
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(
                    if (checked) animatedColor else Color.Transparent,
                    RoundedCornerShape(2.dp)
                )
                .border(
                    1.dp,
                    animatedColor,
                    RoundedCornerShape(2.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Text(
                    text = "✓",
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 8.sp,
                        color = JewelTheme.globalColors.panelBackground
                    )
                )
            }
        }

        // 文本标签
        if (!iconOnly) {
            Text(
                text = if (compact) "清理" else "自动清理上下文",
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = if (compact) 10.sp else 11.sp,
                    color = textColor
                )
            )
        }
    }
}