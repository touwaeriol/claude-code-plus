package com.claudecodeplus.ui.jewel.components.tools.individual

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.claudecodeplus.ui.models.ToolCall
import com.claudecodeplus.ui.models.ToolResult
import com.claudecodeplus.sdk.types.GlobToolUse
import com.claudecodeplus.ui.jewel.components.tools.shared.ToolHeaderDisplay
import com.claudecodeplus.ui.jewel.components.tools.shared.SearchResultDisplay
import com.claudecodeplus.ui.jewel.components.tools.shared.ToolResultDisplay

/**
 * Glob宸ュ叿涓撶敤灞曠ず缁勪欢
 *
 * 馃幆 鑱岃矗锛氫笓闂ㄥ鐞咷lob宸ュ叿鐨勫睍绀? * 馃敡 鐗圭偣锛氭樉绀烘枃浠舵ā寮忓尮閰嶃€佹悳绱㈢粨鏋? */
@Composable
fun GlobToolDisplay(
    toolCall: ToolCall,
    globTool: GlobToolUse,
    showDetails: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 鍙湪闈炶鎯呮ā寮忎笅鏄剧ず宸ュ叿澶撮儴淇℃伅锛堥伩鍏嶅睍寮€鏃堕噸澶嶏級
        if (!showDetails) {
            val subtitle = buildString {
                append("pattern: ${globTool.pattern}")
                if (globTool.path != null) {
                    append(" in ${globTool.path}")
                }
            }

            ToolHeaderDisplay(
                icon = "馃攳",
                toolName = "Glob",
                subtitle = subtitle,
                status = toolCall.status
            )
        }

        // 鏄剧ず鎼滅储缁撴灉
        if (showDetails && toolCall.result != null) {
            when (val result = toolCall.result) {
                is ToolResult.Success -> {
                    // 瑙ｆ瀽杈撳嚭涓烘枃浠跺垪琛?                    val fileList = result.output
                        .split('\n')
                        .filter { it.trim().isNotEmpty() }

                    SearchResultDisplay(
                        results = fileList,
                        searchTerm = globTool.pattern,
                        totalCount = fileList.size
                    )
                }
                is ToolResult.FileSearchResult -> {
                    SearchResultDisplay(
                        results = result.files.map { it.path },
                        searchTerm = globTool.pattern,
                        totalCount = result.totalCount
                    )
                }
                else -> {
                    ToolResultDisplay(result)
                }
            }
        }
    }
}
