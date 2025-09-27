package com.claudecodeplus.ui.jewel.components.tools.individual

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.claudecodeplus.ui.models.ToolCall
import com.claudecodeplus.ui.models.ToolResult
import com.claudecodeplus.sdk.types.KillShellToolUse
import com.claudecodeplus.ui.jewel.components.tools.shared.ToolHeaderDisplay
import com.claudecodeplus.ui.jewel.components.tools.shared.ToolResultDisplay
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

/**
 * KillShell宸ュ叿涓撶敤灞曠ず缁勪欢
 *
 * 馃幆 鑱岃矗锛氫笓闂ㄥ鐞咾illShell宸ュ叿鐨勫睍绀? * 馃敡 鐗圭偣锛氭樉绀篠hell杩涚▼ID銆佺粓姝㈢姸鎬? */
@Composable
fun KillShellDisplay(
    toolCall: ToolCall,
    killShellTool: KillShellToolUse,
    showDetails: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 鍙湪闈炶鎯呮ā寮忎笅鏄剧ず宸ュ叿澶撮儴淇℃伅锛堥伩鍏嶅睍寮€鏃堕噸澶嶏級
        if (!showDetails) {
            ToolHeaderDisplay(
                icon = "⛔",
                toolName = "KillShell",
                subtitle = "shell_id: ${killShellTool.shellId}",
                status = toolCall.status
            )
        }

        // 鏄剧ず缁撴灉
        if (showDetails && toolCall.result != null) {
            when (val result = toolCall.result) {
                is ToolResult.Success -> {
                    Text(
                        text = "鉁?Shell杩涚▼宸茬粓姝? ${killShellTool.shellId}",
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 11.sp,
                            color = androidx.compose.ui.graphics.Color(0xFF4CAF50)
                        )
                    )
                }
                is ToolResult.Failure -> {
                    Text(
                        text = "鉂?缁堟澶辫触: ${result.error}",
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 11.sp,
                            color = androidx.compose.ui.graphics.Color(0xFFFF6B6B)
                        )
                    )
                }
                else -> {
                    ToolResultDisplay(result)
                }
            }
        }
    }
}
