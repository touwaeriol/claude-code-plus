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
import com.claudecodeplus.ui.viewmodels.tool.*
import com.claudecodeplus.ui.jewel.components.tools.individual.*
import com.claudecodeplus.ui.jewel.components.tools.shared.*
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

/**
 * 类型安全的工具调用展示组件
 *
 * 这是新一代的工具展示系统入口，基于强类型的 ViewModel 实现：
 * - 类型安全：充分利用 ToolDetailViewModel 的强类型信息
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
        // 🎯 核心：基于 ViewModel 的类型安全路由到专门组件
        when (val toolDetail = toolCall.viewModel?.toolDetail) {
            is TodoWriteToolDetail -> {
                TodoWriteDisplayV2(
                    toolCall = toolCall,
                    showDetails = showDetails
                )
            }
            is ReadToolDetail -> {
                ReadToolDisplay(
                    toolCall = toolCall,
                    showDetails = showDetails,
                    onFileClick = { ideIntegration?.handleToolClick(toolCall) }
                )
            }
            is EditToolDetail -> {
                EditToolDisplay(
                    toolCall = toolCall,
                    showDetails = showDetails,
                    onFileClick = { ideIntegration?.handleToolClick(toolCall) }
                )
            }
            is BashToolDetail -> {
                BashToolDisplay(
                    toolCall = toolCall,
                    showDetails = showDetails
                )
            }
            is BashOutputToolDetail -> {
                BashOutputDisplay(
                    toolCall = toolCall,
                    showDetails = showDetails
                )
            }
            is KillShellToolDetail -> {
                KillShellDisplay(
                    toolCall = toolCall,
                    showDetails = showDetails
                )
            }
            is ExitPlanModeToolDetail -> {
                ExitPlanModeDisplay(
                    toolCall = toolCall,
                    showDetails = showDetails
                )
            }
            is ListMcpResourcesToolDetail -> {
                ListMcpResourcesDisplay(
                    toolCall = toolCall,
                    showDetails = showDetails
                )
            }
            is ReadMcpResourceToolDetail -> {
                ReadMcpResourceDisplay(
                    toolCall = toolCall,
                    showDetails = showDetails
                )
            }
            is GlobToolDetail -> {
                GlobToolDisplay(
                    toolCall = toolCall,
                    showDetails = showDetails
                )
            }
            is GrepToolDetail -> {
                GrepToolDisplay(
                    toolCall = toolCall,
                    showDetails = showDetails
                )
            }
            is WriteToolDetail -> {
                WriteToolDisplay(
                    toolCall = toolCall,
                    showDetails = showDetails,
                    onFileClick = { ideIntegration?.handleToolClick(toolCall) }
                )
            }
            is MultiEditToolDetail -> {
                MultiEditToolDisplay(
                    toolCall = toolCall,
                    showDetails = showDetails,
                    onFileClick = { ideIntegration?.handleToolClick(toolCall) }
                )
            }
            is WebFetchToolDetail -> {
                WebFetchToolDisplay(
                    toolCall = toolCall,
                    showDetails = showDetails
                )
            }
            is WebSearchToolDetail -> {
                WebSearchToolDisplay(
                    toolCall = toolCall,
                    showDetails = showDetails
                )
            }
            is TaskToolDetail -> {
                TaskToolDisplay(
                    toolCall = toolCall,
                    showDetails = showDetails
                )
            }
            is NotebookEditToolDetail -> {
                NotebookEditToolDisplay(
                    toolCall = toolCall,
                    showDetails = showDetails
                )
            }
            is McpToolDetail -> {
                McpToolDisplay(
                    toolCall = toolCall,
                    showDetails = showDetails
                )
            }
            is GenericToolDetail -> {
                GenericToolDisplay(toolCall, showDetails)
            }
            is SlashCommandToolDetail -> {
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








