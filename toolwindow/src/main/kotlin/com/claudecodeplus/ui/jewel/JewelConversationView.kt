package com.claudecodeplus.ui.jewel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudecodeplus.ui.jewel.components.*
import com.claudecodeplus.ui.models.*
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.Orientation
import androidx.compose.material.LocalContentColor
import java.text.SimpleDateFormat
import java.util.*

/**
 * 重新设计的对话视图
 * 集成了模型选择、Markdown 渲染、工具调用显示等功能
 */
@Composable
fun JewelConversationView(
    messages: List<EnhancedMessage>,
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: (() -> Unit)? = null,
    contexts: List<ContextReference> = emptyList(),
    onContextAdd: (ContextReference) -> Unit = {},
    onContextRemove: (ContextReference) -> Unit = {},
    isGenerating: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(JewelTheme.globalColors.panelBackground)
    ) {
        // 消息列表区域 - 占据剩余空间
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(16.dp),
                reverseLayout = false
            ) {
                items(messages) { message ->
                    MessageBubble(message = message)
                }
            }
        }
        
        // 分隔线
        Divider(
            orientation = org.jetbrains.jewel.ui.Orientation.Horizontal,
            modifier = Modifier.height(1.dp),
            color = JewelTheme.globalColors.borders.normal
        )
        
        // 输入区域 - 使用 EnhancedSmartInputArea
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(JewelTheme.globalColors.panelBackground)
                .padding(8.dp)
        ) {
            EnhancedSmartInputArea(
                text = inputText,
                onTextChange = onInputChange,
                onSend = onSend,
                onStop = onStop,
                contexts = contexts,
                onContextAdd = onContextAdd,
                onContextRemove = onContextRemove,
                isGenerating = isGenerating,
                enabled = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}



/**
 * 消息气泡组件
 */
@Composable
private fun MessageBubble(
    message: EnhancedMessage,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (message.role == MessageRole.USER) Arrangement.End else Arrangement.Start
    ) {
        if (message.role != MessageRole.USER) {
            // Assistant消息 - 左对齐，占据更多空间
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .background(
                        JewelTheme.globalColors.panelBackground,
                        RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        message.content,
                        style = JewelTheme.defaultTextStyle.copy(
                            color = JewelTheme.globalColors.text.normal,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    )
                    
                    // 时间戳
                    Text(
                        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(message.timestamp)),
                        style = JewelTheme.defaultTextStyle.copy(
                            color = JewelTheme.globalColors.text.disabled,
                            fontSize = 10.sp
                        )
                    )
                }
            }
        } else {
            // User消息 - 右对齐，较小宽度
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .background(
                        JewelTheme.globalColors.borders.focused,
                        RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        message.content,
                        style = JewelTheme.defaultTextStyle.copy(
                            color = JewelTheme.globalColors.text.normal,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    )
                    
                    // 时间戳
                    Text(
                        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(message.timestamp)),
                        style = JewelTheme.defaultTextStyle.copy(
                            color = JewelTheme.globalColors.text.disabled,
                            fontSize = 10.sp
                        )
                    )
                }
            }
        }
    }
}




