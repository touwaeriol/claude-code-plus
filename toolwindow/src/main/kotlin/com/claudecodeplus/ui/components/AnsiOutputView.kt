package com.claudecodeplus.ui.jewel.components.tools.output

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.Flow
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import androidx.compose.animation.*
import androidx.compose.animation.core.tween

/**
 * Compose 组件：显示 ANSI 格式的终端输出
 * 
 * @param text 包含 ANSI 转义序列的文本
 * @param modifier Modifier
 * @param maxLines 最大显示行数（默认10行）
 * @param onCopy 复制回调
 */
@Composable
fun AnsiOutputView(
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int = 5,  // 默认减少显示行数
    onCopy: ((String) -> Unit)? = null
) {
    val parser = remember { SimpleAnsiParser() }
    val annotatedString = remember(text) {
        parser.parseAnsiText(text)
    }
    
    // 使用 JewelTheme 的默认文本样式
    val textStyle = JewelTheme.defaultTextStyle.copy(
        fontFamily = FontFamily.Monospace
    )
    
    // 基于文本样式计算高度
    val lineHeightDp = with(LocalDensity.current) {
        textStyle.lineHeight.toDp()
    }
    // 使用与主题一致的 padding（8dp 是常用值）
    val paddingDp = 8.dp
    val calculatedMaxHeight = (lineHeightDp * maxLines) + (paddingDp * 2)
    
    // 悬停状态和复制状态
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    var copied by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    
    // 复制状态重置
    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(2000) // 2秒后重置
            copied = false
        }
    }
    
    // 终端显示区域 - 使用 Box 支持悬停按钮
    SelectionContainer {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(max = calculatedMaxHeight)
                .background(
                    // 使用主题相对应的终端背景：浅色主题用浅灰，深色主题用深灰
                    if (JewelTheme.isDark) {
                        Color(40, 44, 52)  // 深色主题：VS Code深色背景
                    } else {
                        Color(248, 248, 248)  // 浅色主题：浅灰背景
                    },
                    shape = RoundedCornerShape(4.dp)
                )
                .hoverable(interactionSource)
                .padding(paddingDp)
        ) {
            val scrollState = rememberScrollState()
            
            Text(
                text = annotatedString,
                style = textStyle.copy(
                    color = if (JewelTheme.isDark) {
                        Color(220, 220, 220)  // 深色主题：亮灰色文字
                    } else {
                        Color(60, 60, 60)     // 浅色主题：深灰色文字
                    },
                    fontSize = 11.sp  // 减小字体大小
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            )
            
            // 右上角浮动复制按钮 - 仅在有复制回调且悬停时显示
            if (onCopy != null) {
                AnimatedVisibility(
                    visible = isHovered,
                    enter = fadeIn(animationSpec = tween(200)),
                    exit = fadeOut(animationSpec = tween(200)),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .background(
                                JewelTheme.globalColors.panelBackground.copy(alpha = 0.9f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        // 复制按钮（带剪贴板emoji）
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clickable {
                                    // 复制纯文本（去除 ANSI 转义序列）
                                    val plainText = text.replace(Regex("\u001B\\[[0-9;]*m"), "")
                                    clipboardManager.setText(AnnotatedString(plainText))
                                    onCopy(plainText)
                                    copied = true
                                }
                        ) {
                            Text(
                                text = if (copied) "✓" else "📋",
                                style = JewelTheme.defaultTextStyle.copy(
                                    fontSize = 12.sp,
                                    color = if (copied) Color(0xFF4CAF50) else JewelTheme.globalColors.text.normal.copy(alpha = 0.7f)
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 流式 ANSI 输出视图
 * 支持实时显示命令输出
 */
@Composable
fun StreamingAnsiOutputView(
    outputFlow: Flow<String>,
    modifier: Modifier = Modifier,
    maxLines: Int = 10
) {
    val parser = remember { SimpleAnsiParser() }
    var fullText by remember { mutableStateOf("") }
    
    // 收集输出流
    LaunchedEffect(outputFlow) {
        outputFlow.collect { line ->
            fullText += line
        }
    }
    
    val annotatedString = remember(fullText) {
        parser.parseAnsiText(fullText)
    }
    
    // 使用 JewelTheme 的默认文本样式
    val textStyle = JewelTheme.defaultTextStyle.copy(
        fontFamily = FontFamily.Monospace
    )
    
    // 基于文本样式计算高度
    val lineHeightDp = with(LocalDensity.current) {
        textStyle.lineHeight.toDp()
    }
    val paddingDp = 8.dp
    val calculatedMaxHeight = (lineHeightDp * maxLines) + (paddingDp * 2)
    
    SelectionContainer {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(max = calculatedMaxHeight)
                .background(
                    // 使用主题相对应的终端背景
                    if (JewelTheme.isDark) {
                        Color(40, 44, 52)  // 深色主题：VS Code深色背景
                    } else {
                        Color(248, 248, 248)  // 浅色主题：浅灰背景
                    }
                )
                .padding(paddingDp)
        ) {
            val scrollState = rememberScrollState()
            
            // 自动滚动到底部
            LaunchedEffect(fullText) {
                scrollState.animateScrollTo(scrollState.maxValue)
            }
            
            Text(
                text = annotatedString,
                style = textStyle.copy(
                    color = if (JewelTheme.isDark) {
                        Color(220, 220, 220)  // 深色主题：亮灰色文字
                    } else {
                        Color(60, 60, 60)     // 浅色主题：深灰色文字
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            )
        }
    }
}

/**
 * 带有加载状态的 ANSI 输出视图
 */
@Composable
fun AnsiOutputViewWithLoading(
    text: String?,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    maxLines: Int = 10
) {
    Box(modifier = modifier) {
        when {
            isLoading -> {
                // 显示加载中
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(
                            // 使用主题相对应的终端背景
                            if (JewelTheme.isDark) {
                                Color(40, 44, 52)  // 深色主题：VS Code深色背景
                            } else {
                                Color(248, 248, 248)  // 浅色主题：浅灰背景
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "正在执行...",
                        color = if (JewelTheme.isDark) {
                            Color(220, 220, 220)  // 深色主题：亮灰色文字
                        } else {
                            Color(60, 60, 60)     // 浅色主题：深灰色文字
                        }
                    )
                }
            }
            text != null -> {
                // 显示输出
                AnsiOutputView(
                    text = text,
                    maxLines = maxLines
                )
            }
            else -> {
                // 无内容
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(
                            // 使用主题相对应的终端背景
                            if (JewelTheme.isDark) {
                                Color(40, 44, 52)  // 深色主题：VS Code深色背景
                            } else {
                                Color(248, 248, 248)  // 浅色主题：浅灰背景
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "无输出",
                        color = if (JewelTheme.isDark) {
                            Color(120, 120, 120)  // 深色主题：中灰色
                        } else {
                            Color(150, 150, 150)  // 浅色主题：中灰色
                        }
                    )
                }
            }
        }
    }
}