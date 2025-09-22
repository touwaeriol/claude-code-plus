package com.claudecodeplus.ui.jewel.components.tools.individual

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudecodeplus.sdk.types.McpToolUse
import com.claudecodeplus.ui.jewel.components.tools.shared.ToolHeaderDisplay
import com.claudecodeplus.ui.jewel.components.tools.shared.ToolResultDisplay
import com.claudecodeplus.ui.models.ToolCall
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

/**
 * MCP Tool 展示组件
 */
@Composable
fun McpToolDisplay(
    toolCall: ToolCall,
    mcpTool: McpToolUse,
    showDetails: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (!showDetails) {
            ToolHeaderDisplay(
                icon = "MCP",
                toolName = mcpTool.fullToolName,
                subtitle = "${mcpTool.serverName}.${mcpTool.functionName}",
                status = toolCall.status
            )
        }

        if (showDetails) {
            Text(
                text = "服务器：${mcpTool.serverName}",
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            Text(
                text = "函数：${mcpTool.functionName}",
                style = JewelTheme.defaultTextStyle.copy(fontSize = 11.sp)
            )

            if (mcpTool.parameters.isNotEmpty()) {
                Text(
                    text = "参数：",
                    style = JewelTheme.defaultTextStyle.copy(fontSize = 11.sp)
                )
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        mcpTool.parameters.forEach { (key, value) ->
                            Text(
                                text = "$key = ${formatParameterValue(value)}",
                                style = JewelTheme.defaultTextStyle.copy(
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = JewelTheme.globalColors.text.normal.copy(alpha = 0.8f)
                                )
                            )
                        }
                    }
                }
            }

            toolCall.result?.let { result ->
                ToolResultDisplay(result)
            }
        }
    }
}

private fun formatParameterValue(value: Any?): String {
    return when (value) {
        null -> "null"
        is String -> if (value.length > 60) value.take(57) + "..." else value
        is Number, is Boolean -> value.toString()
        is Iterable<*> -> value.joinToString(prefix = "[", postfix = "]") { formatParameterValue(it) }
        is Map<*, *> -> value.entries.joinToString(prefix = "{", postfix = "}") { (k, v) ->
            "${k.toString()}=${formatParameterValue(v)}"
        }
        else -> value.toString()
    }
}
