@file:OptIn(ExperimentalFoundationApi::class, org.jetbrains.jewel.foundation.ExperimentalJewelApi::class)

package com.claudecodeplus.ui.jewel.components.tools

import com.claudecodeplus.core.logging.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudecodeplus.ui.models.ToolCall
import com.claudecodeplus.ui.models.ToolCallStatus
import com.claudecodeplus.sdk.types.*
import com.claudecodeplus.ui.jewel.components.tools.individual.*
import com.claudecodeplus.ui.jewel.components.tools.shared.*
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

/**
 * 类型安全的工具调用展示组件
 *
 * 这是新一代的工具展示系统入口，基于强类型的SpecificToolUse实现：
 * - 类型安全：充分利用SpecificToolUse的强类型信息
 * - 多态展示：根据具体工具类型自动选择合适的展示组件
 * - 可扩展：新增工具类型只需添加对应的展示组件
 * - 可复用：相似工具共享展示逻辑
 */
@Composable
fun TypedToolCallDisplay(
    toolCall: ToolCall,
    modifier: Modifier = Modifier,
    showDetails: Boolean = true,
    ideIntegration: com.claudecodeplus.ui.services.IdeIntegration? = null
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // ?? 核心：基于SpecificToolUse的类型安全路由到专门组件
        when (val specificTool = toolCall.specificTool) {
            is TodoWriteToolUse -> {
                TodoWriteDisplayV2(
                    toolCall = toolCall,
                    todoWriteTool = specificTool,
                    showDetails = showDetails
                )
            }
            is ReadToolUse -> {
                // logD("[TypedToolCallDisplay] ?? 路由到ReadToolDisplay: ${specificTool.filePath}")
                ReadToolDisplay(
                    toolCall = toolCall,
                    readTool = specificTool,
                    showDetails = showDetails,
                    onFileClick = { ideIntegration?.handleToolClick(toolCall) }
                )
            }
            is EditToolUse -> {
    //                 logD("[TypedToolCallDisplay] ?? 路由到EditToolDisplay: ${specificTool.filePath}")
                EditToolDisplay(
                    toolCall = toolCall,
                    editTool = specificTool,
                    showDetails = showDetails,
                    onFileClick = { ideIntegration?.handleToolClick(toolCall) }
                )
            }
            is BashToolUse -> {
                // logD("[TypedToolCallDisplay] ?? 路由到BashToolDisplay: ${specificTool.command}")
                BashToolDisplay(
                    toolCall = toolCall,
                    bashTool = specificTool,
                    showDetails = showDetails
                )
            }
            is BashOutputToolUse -> {
                // logD("[TypedToolCallDisplay] ?? 路由到BashOutputDisplay: ${specificTool.bashId}")
                BashOutputDisplay(
                    toolCall = toolCall,
                    bashOutputTool = specificTool,
                    showDetails = showDetails
                )
            }
            is KillShellToolUse -> {
    //                 logD("[TypedToolCallDisplay] ? 路由到KillShellDisplay: ${specificTool.shellId}")
                KillShellDisplay(
                    toolCall = toolCall,
                    killShellTool = specificTool,
                    showDetails = showDetails
                )
            }
            is ExitPlanModeToolUse -> {
                // logD("[TypedToolCallDisplay] ?? 路由到ExitPlanModeDisplay")
                ExitPlanModeDisplay(
                    toolCall = toolCall,
                    exitPlanModeTool = specificTool,
                    showDetails = showDetails
                )
            }
            is ListMcpResourcesToolUse -> {
                // logD("[TypedToolCallDisplay] ?? 路由到ListMcpResourcesDisplay: ${specificTool.server}")
                ListMcpResourcesDisplay(
                    toolCall = toolCall,
                    listMcpResourcesTool = specificTool,
                    showDetails = showDetails
                )
            }
            is ReadMcpResourceToolUse -> {
                // logD("[TypedToolCallDisplay] ?? 路由到ReadMcpResourceDisplay: ${specificTool.server}/${specificTool.uri}")
                ReadMcpResourceDisplay(
                    toolCall = toolCall,
                    readMcpResourceTool = specificTool,
                    showDetails = showDetails
                )
            }
            is GlobToolUse -> {
                // logD("[TypedToolCallDisplay] ?? 路由到GlobToolDisplay: ${specificTool.pattern}")
                GlobToolDisplay(
                    toolCall = toolCall,
                    globTool = specificTool,
                    showDetails = showDetails
                )
            }
            is GrepToolUse -> {
                // logD("[TypedToolCallDisplay] ?? 路由到GrepToolDisplay: ${specificTool.pattern}")
                GrepToolDisplay(
                    toolCall = toolCall,
                    grepTool = specificTool,
                    showDetails = showDetails
                )
            }
            // 其他未专门实现的工具使用通用展示
            is WriteToolUse -> {
                WriteToolDisplay(
                    toolCall = toolCall,
                    writeTool = specificTool,
                    showDetails = showDetails,
                    onFileClick = { ideIntegration?.handleToolClick(toolCall) }
                )
            }
            is MultiEditToolUse -> {
                MultiEditToolDisplay(
                    toolCall = toolCall,
                    multiEditTool = specificTool,
                    showDetails = showDetails,
                    onFileClick = { ideIntegration?.handleToolClick(toolCall) }
                )
            }
            is WebFetchToolUse -> {
                WebFetchToolDisplay(
                    toolCall = toolCall,
                    webFetchTool = specificTool,
                    showDetails = showDetails
                )
            }
            is WebSearchToolUse -> {
                WebSearchToolDisplay(
                    toolCall = toolCall,
                    webSearchTool = specificTool,
                    showDetails = showDetails
                )
            }
            is TaskToolUse -> {
                TaskToolDisplay(
                    toolCall = toolCall,
                    taskTool = specificTool,
                    showDetails = showDetails
                )
            }
            is NotebookEditToolUse -> {
                NotebookEditToolDisplay(
                    toolCall = toolCall,
                    notebookEditTool = specificTool,
                    showDetails = showDetails
                )
            }
            is McpToolUse -> {
                McpToolDisplay(
                    toolCall = toolCall,
                    mcpTool = specificTool,
                    showDetails = showDetails
                )
            }
            is UnknownToolUse -> {
                GenericToolDisplay(toolCall, showDetails)
            }
            null -> {
                FallbackToolDisplay(
                    toolCall = toolCall,
                    showDetails = showDetails,
                    ideIntegration = ideIntegration
                )
            }
        }
    }
}


/**
 * 通用工具展示组件（处理未知类型）
 */
@Composable
private fun GenericToolDisplay(
    toolCall: ToolCall,
    showDetails: Boolean
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ToolHeaderDisplay(
            icon = "GEN",
            toolName = toolCall.name,
            subtitle = "Generic Tool",
            status = toolCall.status
        )

        if (showDetails && toolCall.result != null) {
            ToolResultDisplay(toolCall.result!!)
        }
    }
}

/**
 * 回退展示组件（向后兼容，没有specificTool时使用）
 */
@Composable
private fun FallbackToolDisplay(
    toolCall: ToolCall,
    showDetails: Boolean,
    ideIntegration: com.claudecodeplus.ui.services.IdeIntegration?
) {
    // 使用原有的CompactToolCallDisplay逻辑作为回退
    //     logD("[FallbackToolDisplay] 使用回退逻辑处理工具: ${toolCall.name}")

    // 这里可以调用原有的展示逻辑，或者实现简化版本
    GenericToolDisplay(toolCall, showDetails)
}








