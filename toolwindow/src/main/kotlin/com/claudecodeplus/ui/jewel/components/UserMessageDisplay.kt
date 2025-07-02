package com.claudecodeplus.ui.jewel.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.claudecodeplus.ui.models.*
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import java.text.SimpleDateFormat
import java.util.*

/**
 * 用户消息显示组件
 * 复用 ChatInputArea 的设计和布局，但使用只读显示模式
 * 
 * 设计特点：
 * - 使用与 ChatInputArea 相同的统一边框和背景
 * - 三行布局结构：上下文标签 → 消息内容 → 模型信息
 * - 隐藏交互元素（Add Context 按钮、发送按钮）
 * - 保持视觉一致性
 */
@Composable
fun UserMessageDisplay(
    message: EnhancedMessage,
    contexts: List<ContextReference> = emptyList(),
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                JewelTheme.globalColors.panelBackground,
                RoundedCornerShape(8.dp)
            )
            .border(
                1.dp,
                JewelTheme.globalColors.borders.normal,
                RoundedCornerShape(8.dp)
            )
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 第一行：上下文标签区域（仅在有上下文时显示）
        if (contexts.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 上下文标签滚动列表
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(contexts) { context ->
                        ReadOnlyContextTag(
                            context = context,
                            modifier = Modifier.height(20.dp)
                        )
                    }
                }
            }
        }
        
        // 第二行：消息内容区域
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            // 消息文本内容
            Column(
                modifier = Modifier.weight(1f)
            ) {
                val (_, userText) = parseMessageContent(message.content)
                val formattedText = formatInlineReferences(userText)
                
                // 使用简单的文本显示，保持与输入框相同的字体样式
                Text(
                    text = formattedText,
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        
        // 第三行：底部信息行（模型信息和时间戳）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：使用的模型信息（只显示，不可切换）
            message.model?.let { model ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "🤖",
                        style = JewelTheme.defaultTextStyle.copy(fontSize = 8.sp)
                    )
                    Text(
                        text = model.displayName,
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 9.sp,
                            color = JewelTheme.globalColors.text.normal.copy(alpha = 0.7f)
                        )
                    )
                }
            } ?: run {
                // 如果没有模型信息，显示空白
                Spacer(modifier = Modifier.width(1.dp))
            }
            
            // 右侧：时间戳
            Text(
                text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(message.timestamp)),
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 9.sp,
                    color = JewelTheme.globalColors.text.normal.copy(alpha = 0.6f)
                )
            )
        }
    }
}

/**
 * 解析消息内容，分离上下文和用户文本
 */
private fun parseMessageContent(content: String): Pair<List<String>, String> {
    val contextRegex = "^> \\*\\*上下文资料\\*\\*\\n(?:> \\n)?((?:> - .+\\n)+)\\n".toRegex()
    val match = contextRegex.find(content)
    
    return if (match != null) {
        val contextSection = match.groupValues[1]
        val contexts = contextSection
            .split('\n')
            .filter { it.startsWith("> - ") }
            .map { it.substring(4) } // 移除 "> - " 前缀
        
        val userMessage = content.substring(match.range.last + 1)
        Pair(contexts, userMessage)
    } else {
        Pair(emptyList(), content)
    }
}

/**
 * 将内联引用转换为简短显示格式
 */
private fun formatInlineReferences(text: String): String {
    val pattern = "@([^\\s@]+)".toRegex()
    return pattern.replace(text) { matchResult ->
        val fullPath = matchResult.groupValues[1]
        // 如果是完整路径，提取文件名；否则保持原样
        if (fullPath.contains('/')) {
            "@${fullPath.substringAfterLast('/')}"
        } else {
            matchResult.value
        }
    }
}

/**
 * 只读上下文标签组件
 * 与 ContextTag 相同的设计，但无删除功能
 */
@Composable
private fun ReadOnlyContextTag(
    context: ContextReference,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(
                JewelTheme.globalColors.panelBackground,
                RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 标签文本
        Text(
            text = getDisplayTextForReadOnly(context),
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 11.sp,
                color = JewelTheme.globalColors.text.normal
            )
        )
    }
}

/**
 * 获取只读上下文的显示文本
 */
private fun getDisplayTextForReadOnly(context: ContextReference): String {
    return when (context) {
        is ContextReference.FileReference -> {
            // 文件：显示 @filename
            val filename = context.path.substringAfterLast('/')
                .ifEmpty { context.path.substringAfterLast('\\') }
                .ifEmpty { context.path }
            "@$filename"
        }
        is ContextReference.WebReference -> {
            // Web：显示完整URL
            "@${context.url}"
        }
        is ContextReference.FolderReference -> "@${context.path.substringAfterLast('/')}"
        is ContextReference.SymbolReference -> "@${context.name}"
        is ContextReference.TerminalReference -> "@terminal"
        is ContextReference.ProblemsReference -> "@problems"
        is ContextReference.GitReference -> "@git"
        is ContextReference.ImageReference -> "@${context.filename}"
        ContextReference.SelectionReference -> "@selection"
        ContextReference.WorkspaceReference -> "@workspace"
    }
}

/**
 * 从消息内容中提取上下文引用
 */
fun extractContextReferences(content: String): List<ContextReference> {
    val (contexts, _) = parseMessageContent(content)
    return contexts.map { contextText ->
        // 简单地解析上下文文本并创建对应的ContextReference
        when {
            contextText.contains("📄") -> {
                val path = contextText.substringAfter('`').substringBefore('`')
                ContextReference.FileReference(path = path, fullPath = path)
            }
            contextText.contains("🌐") -> {
                val url = contextText.substringAfter("🌐 ").substringBefore(" (")
                val title = if (contextText.contains(" (") && contextText.contains(")")) {
                    contextText.substringAfter(" (").substringBefore(")")
                } else null
                ContextReference.WebReference(url = url, title = title)
            }
            contextText.contains("📁") -> {
                val path = contextText.substringAfter('`').substringBefore('`')
                val fileCountText = contextText.substringAfter("(").substringBefore("个文件)")
                val fileCount = fileCountText.toIntOrNull() ?: 0
                ContextReference.FolderReference(path = path, fileCount = fileCount)
            }
            contextText.contains("🖼") -> {
                val filename = contextText.substringAfter('`').substringBefore('`')
                ContextReference.ImageReference(path = filename, filename = filename)
            }
            else -> {
                // 默认作为文件引用处理
                ContextReference.FileReference(path = contextText, fullPath = contextText)
            }
        }
    }
} 