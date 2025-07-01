package com.claudecodeplus.ui.jewel.components.context

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.foundation.theme.JewelTheme

/**
 * 上下文类型选择器
 * 第一步：选择上下文类型（File 或 Web）
 */
@Composable
fun ContextTypeSelector(
    config: ContextSelectorConfig,
    onTypeSelected: (ContextType) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 可选择的上下文类型
    val contextTypes = remember {
        listOf(
            ContextTypeInfo(
                type = ContextType.File,
                icon = "📁",
                name = "File",
                description = "选择项目文件"
            ),
            ContextTypeInfo(
                type = ContextType.Web,
                icon = "🌐",
                name = "Web",
                description = "输入网页链接"
            )
        )
    }
    
    var selectedIndex by remember { mutableStateOf(0) }
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    
    // 自动聚焦
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionUp -> {
                            selectedIndex = (selectedIndex - 1).coerceAtLeast(0)
                            true
                        }
                        Key.DirectionDown -> {
                            selectedIndex = (selectedIndex + 1).coerceAtMost(contextTypes.size - 1)
                            true
                        }
                        Key.Enter -> {
                            onTypeSelected(contextTypes[selectedIndex].type)
                            true
                        }
                        Key.Escape -> {
                            onCancel()
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
    ) {
        // 标题
        Text(
            text = "选择上下文类型",
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 12.sp,
                color = JewelTheme.globalColors.text.disabled
            ),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        // 类型列表
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(contextTypes) { typeInfo ->
                val isSelected = contextTypes.indexOf(typeInfo) == selectedIndex
                ContextTypeItem(
                    typeInfo = typeInfo,
                    isSelected = isSelected,
                    config = config,
                    onClick = { onTypeSelected(typeInfo.type) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        
        // 提示信息
        Text(
            text = "使用 ↑↓ 导航，Enter 选择，Esc 取消",
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 10.sp,
                color = JewelTheme.globalColors.text.disabled
            ),
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

/**
 * 上下文类型信息
 */
private data class ContextTypeInfo(
    val type: ContextType,
    val icon: String,
    val name: String,
    val description: String
)

/**
 * 上下文类型选择项
 */
@Composable
private fun ContextTypeItem(
    typeInfo: ContextTypeInfo,
    isSelected: Boolean,
    config: ContextSelectorConfig,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(config.itemHeight.dp)
            .clip(RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .then(
                if (isSelected) {
                    Modifier.background(
                        JewelTheme.globalColors.borders.focused.copy(alpha = 0.15f),
                        RoundedCornerShape(4.dp)
                    )
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        // 图标
        Text(
            text = typeInfo.icon,
            style = JewelTheme.defaultTextStyle.copy(fontSize = 16.sp),
            modifier = Modifier.padding(end = 8.dp)
        )
        
        // 名称和描述
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = typeInfo.name,
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 12.sp,
                    color = JewelTheme.globalColors.text.normal
                )
            )
            Text(
                text = typeInfo.description,
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 10.sp,
                    color = JewelTheme.globalColors.text.disabled
                )
            )
        }
        
        // 选中指示器
        if (isSelected) {
            Text(
                text = "→",
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 12.sp,
                    color = JewelTheme.globalColors.borders.focused
                )
            )
        }
    }
}

 