package com.claudecodeplus.ui.jewel.components.tools.individual

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.claudecodeplus.ui.models.ToolCall
import com.claudecodeplus.ui.models.ToolResult
import com.claudecodeplus.sdk.types.GrepToolUse
import com.claudecodeplus.ui.jewel.components.tools.shared.ToolHeaderDisplay
import com.claudecodeplus.ui.jewel.components.tools.shared.SearchResultDisplay
import com.claudecodeplus.ui.jewel.components.tools.shared.ToolResultDisplay

/**
 * Grep宸ュ叿涓撶敤灞曠ず缁勪欢
 *
 * 馃幆 鑱岃矗锛氫笓闂ㄥ鐞咷rep宸ュ叿鐨勫睍绀? * 馃敡 鐗圭偣锛氭樉绀烘枃鏈悳绱€佸尮閰嶇粨鏋溿€佹悳绱㈤€夐」
 */
@Composable
fun GrepToolDisplay(
    toolCall: ToolCall,
    grepTool: GrepToolUse,
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
                append("search: ${grepTool.pattern}")
                when {
                    grepTool.glob != null -> append(" in ${grepTool.glob}")
                    grepTool.type != null -> append(" in *.${grepTool.type}")
                    grepTool.path != null -> append(" in ${grepTool.path}")
                }
                if (grepTool.caseInsensitive) append(" [蹇界暐澶у皬鍐橾")
                if (grepTool.showLineNumbers) append(" [鏄剧ず琛屽彿]")
            }

            ToolHeaderDisplay(
                icon = "馃攳",
                toolName = "Grep",
                subtitle = subtitle,
                status = toolCall.status
            )
        }

        // 鏄剧ず鎼滅储缁撴灉
        if (showDetails && toolCall.result != null) {
            when (val result = toolCall.result) {
                is ToolResult.Success -> {
                    // 瑙ｆ瀽鎼滅储缁撴灉
                    val searchResults = result.output
                        .split('\n')
                        .filter { it.trim().isNotEmpty() }

                    SearchResultDisplay(
                        results = searchResults,
                        searchTerm = grepTool.pattern,
                        totalCount = searchResults.size
                    )
                }
                else -> {
                    ToolResultDisplay(result)
                }
            }
        }
    }
}
