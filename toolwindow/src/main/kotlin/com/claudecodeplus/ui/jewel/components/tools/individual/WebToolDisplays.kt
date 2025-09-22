package com.claudecodeplus.ui.jewel.components.tools.individual

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudecodeplus.sdk.types.WebFetchToolUse
import com.claudecodeplus.sdk.types.WebSearchToolUse
import com.claudecodeplus.ui.jewel.components.tools.shared.ToolHeaderDisplay
import com.claudecodeplus.ui.jewel.components.tools.shared.ToolResultDisplay
import com.claudecodeplus.ui.models.ToolCall
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

/**
 * WebFetch 工具展示组件
 */
@Composable
fun WebFetchToolDisplay(
    toolCall: ToolCall,
    webFetchTool: WebFetchToolUse,
    showDetails: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (!showDetails) {
            val domain = webFetchTool.url
                .removePrefix("https://")
                .removePrefix("http://")
                .substringBefore('/')
            ToolHeaderDisplay(
                icon = "WEB",
                toolName = "WebFetch",
                subtitle = domain,
                status = toolCall.status
            )
        }

        if (showDetails) {
            Text(
                text = "URL：${webFetchTool.url}",
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            )

            if (!webFetchTool.prompt.isNullOrBlank()) {
                Text(
                    text = "Prompt：",
                    style = JewelTheme.defaultTextStyle.copy(fontSize = 11.sp)
                )
                SelectionContainer {
                    Text(
                        text = webFetchTool.prompt,
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 11.sp,
                            color = JewelTheme.globalColors.text.normal.copy(alpha = 0.8f)
                        )
                    )
                }
            }

            toolCall.result?.let { result ->
                ToolResultDisplay(result)
            }
        }
    }
}

/**
 * WebSearch 工具展示组件
 */
@Composable
fun WebSearchToolDisplay(
    toolCall: ToolCall,
    webSearchTool: WebSearchToolUse,
    showDetails: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (!showDetails) {
            ToolHeaderDisplay(
                icon = "SEARCH",
                toolName = "WebSearch",
                subtitle = webSearchTool.query.take(30),
                status = toolCall.status
            )
        }

        if (showDetails) {
            Text(
                text = "查询：${webSearchTool.query}",
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            )

            webSearchTool.allowedDomains?.takeIf { it.isNotEmpty() }?.let { domains ->
                Text(
                    text = "允许域名：${domains.joinToString()}",
                    style = JewelTheme.defaultTextStyle.copy(fontSize = 11.sp)
                )
            }

            webSearchTool.blockedDomains?.takeIf { it.isNotEmpty() }?.let { domains ->
                Text(
                    text = "禁止域名：${domains.joinToString()}",
                    style = JewelTheme.defaultTextStyle.copy(fontSize = 11.sp)
                )
            }

            toolCall.result?.let { result ->
                ToolResultDisplay(result)
            }
        }
    }
}
