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
import com.claudecodeplus.ui.viewmodels.tool.WebFetchToolDetail
import com.claudecodeplus.ui.viewmodels.tool.WebSearchToolDetail
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
    showDetails: Boolean = true,
    modifier: Modifier = Modifier
) {
    val toolDetail = toolCall.viewModel?.toolDetail as? WebFetchToolDetail
    if (toolDetail == null) {
        Text("错误：无法获取 WebFetch 工具详情")
        return
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (!showDetails) {
            val domain = toolDetail.url
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
                text = "URL: ${toolDetail.url}",
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            )

            if (!toolDetail.prompt.isNullOrBlank()) {
                Text(
                    text = "Prompt:",
                    style = JewelTheme.defaultTextStyle.copy(fontSize = 11.sp)
                )
                SelectionContainer {
                    Text(
                        text = toolDetail.prompt,
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
    showDetails: Boolean = true,
    modifier: Modifier = Modifier
) {
    val toolDetail = toolCall.viewModel?.toolDetail as? WebSearchToolDetail
    if (toolDetail == null) {
        Text("错误：无法获取 WebSearch 工具详情")
        return
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (!showDetails) {
            ToolHeaderDisplay(
                icon = "SEARCH",
                toolName = "WebSearch",
                subtitle = toolDetail.query.take(30),
                status = toolCall.status
            )
        }

        if (showDetails) {
            Text(
                text = "查询: ${toolDetail.query}",
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            )

            toolDetail.allowedDomains?.takeIf { it.isNotEmpty() }?.let { domains ->
                Text(
                    text = "允许域名: ${domains.joinToString()}",
                    style = JewelTheme.defaultTextStyle.copy(fontSize = 11.sp)
                )
            }

            toolDetail.blockedDomains?.takeIf { it.isNotEmpty() }?.let { domains ->
                Text(
                    text = "禁止域名: ${domains.joinToString()}",
                    style = JewelTheme.defaultTextStyle.copy(fontSize = 11.sp)
                )
            }

            toolCall.result?.let { result ->
                ToolResultDisplay(result)
            }
        }
    }
}
