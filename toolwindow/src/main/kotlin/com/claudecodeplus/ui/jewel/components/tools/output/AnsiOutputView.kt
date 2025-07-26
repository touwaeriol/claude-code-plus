package com.claudecodeplus.ui.jewel.components.tools.output

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.Flow
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.DefaultButton

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
    maxLines: Int = 10,
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
    
    Column(modifier = modifier) {
        // 终端显示区域
        SelectionContainer {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = calculatedMaxHeight)
                    .background(AnsiColors.DEFAULT_BACKGROUND)
                    .padding(paddingDp)
            ) {
                val scrollState = rememberScrollState()
                
                Text(
                    text = annotatedString,
                    style = textStyle.copy(
                        color = AnsiColors.DEFAULT_FOREGROUND
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                )
            }
        }
        
        // 操作栏
        if (onCopy != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(JewelTheme.globalColors.panelBackground)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                DefaultButton(
                    onClick = { 
                        // 复制纯文本（去除 ANSI 转义序列）
                        val plainText = text.replace(Regex("\u001B\\[[0-9;]*m"), "")
                        onCopy(plainText)
                    }
                ) {
                    Text("复制")
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
                .background(AnsiColors.DEFAULT_BACKGROUND)
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
                    color = AnsiColors.DEFAULT_FOREGROUND
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
                        .background(AnsiColors.DEFAULT_BACKGROUND),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "正在执行...",
                        color = AnsiColors.DEFAULT_FOREGROUND
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
                        .background(AnsiColors.DEFAULT_BACKGROUND),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "无输出",
                        color = Color.Gray
                    )
                }
            }
        }
    }
}