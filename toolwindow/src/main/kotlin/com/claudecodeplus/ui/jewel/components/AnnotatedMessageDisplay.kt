/*
 * AnnotatedMessageDisplay.kt
 * 
 * 显示带有可点击上下文引用的消息
 */

package com.claudecodeplus.ui.jewel.components

import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudecodeplus.ui.models.EnhancedMessage
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import java.text.SimpleDateFormat
import java.util.*

/**
 * 带注解的消息显示组件
 * 
 * @param message 消息内容（Markdown 格式）
 * @param timestamp 时间戳
 * @param onContextClick 上下文点击回调
 * @param modifier 修饰符
 */
@Composable
fun AnnotatedMessageDisplay(
    message: String,
    timestamp: Long? = null,
    onContextClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val annotatedText = rememberParsedMarkdown(
        markdown = message,
        linkColor = null // 使用默认颜色
    )
    
    val uriHandler = LocalUriHandler.current
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 消息内容
        SelectionContainer {
            ClickableText(
                text = annotatedText,
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                ),
                onClick = { offset ->
                    handleTextClick(
                        annotatedText = annotatedText,
                        offset = offset,
                        uriHandler = uriHandler,
                        onContextClick = onContextClick
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        // 时间戳（可选）
        timestamp?.let {
            Text(
                text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(it)),
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 11.sp,
                    color = JewelTheme.globalColors.text.disabled
                )
            )
        }
    }
}

/**
 * 处理文本点击事件
 */
private fun handleTextClick(
    annotatedText: androidx.compose.ui.text.AnnotatedString,
    offset: Int,
    uriHandler: androidx.compose.ui.platform.UriHandler,
    onContextClick: (String) -> Unit
) {
    // 获取点击位置的上下文 URI
    val contextUri = getContextUriAtOffset(annotatedText, offset)
    
    if (contextUri != null) {
        when {
            // 处理文件引用
            contextUri.startsWith("file://") -> {
                onContextClick(contextUri)
            }
            // 处理网页链接
            contextUri.startsWith("http://") || contextUri.startsWith("https://") -> {
                try {
                    uriHandler.openUri(contextUri)
                } catch (e: Exception) {
                    // 无法打开链接: $contextUri
                    onContextClick(contextUri)
                }
            }
            // 处理自定义上下文协议
            contextUri.startsWith("claude-context://") -> {
                onContextClick(contextUri)
            }
            else -> {
                // 未知的上下文类型: $contextUri
            }
        }
    }
}

/**
 * 增强消息显示组件（包含模型信息）
 */
@Composable
fun EnhancedMessageDisplay(
    message: EnhancedMessage,
    onContextClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 模型信息
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "🤖",
                style = JewelTheme.defaultTextStyle.copy(fontSize = 12.sp)
            )
            Text(
                text = message.model?.displayName ?: "Unknown",
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 12.sp,
                    color = JewelTheme.globalColors.text.normal.copy(alpha = 0.7f)
                )
            )
        }
        
        // 消息内容
        AnnotatedMessageDisplay(
            message = message.content,
            timestamp = message.timestamp,
            onContextClick = onContextClick
        )
    }
}