@file:OptIn(ExperimentalFoundationApi::class, org.jetbrains.jewel.foundation.ExperimentalJewelApi::class)

package com.claudecodeplus.ui.jewel.components.tools.shared

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudecodeplus.ui.models.ToolCall
import com.claudecodeplus.ui.models.ToolCallStatus
import com.claudecodeplus.ui.models.ToolResult
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

/**
 * 搴曞眰鍙鐢ㄧ粍浠堕泦鍚? *
 * 杩欎簺缁勪欢鍙互琚涓伐鍏峰睍绀虹粍浠跺鐢紝閬靛惊DRY鍘熷垯銆? * 姣忎釜缁勪欢閮芥湁鏄庣‘鐨勮亴璐ｅ拰鎺ュ彛銆? */

/**
 * 宸ュ叿澶撮儴鏄剧ず缁勪欢锛堝彲澶嶇敤锛? * 鐢ㄤ簬鏄剧ず宸ュ叿鐨勫熀鏈俊鎭細鍥炬爣銆佸悕绉般€佸壇鏍囬銆佺姸鎬? */
@Composable
fun ToolHeaderDisplay(
    icon: String,
    toolName: String,
    subtitle: String,
    status: ToolCallStatus,
    onHeaderClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .then(
                if (onHeaderClick != null) {
                    Modifier.clickable { onHeaderClick() }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 宸ュ叿鍥炬爣
        Text(
            text = icon,
            style = JewelTheme.defaultTextStyle.copy(fontSize = 14.sp)
        )

        // 宸ュ叿鍚嶇О鍜屽壇鏍囬
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = toolName,
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = JewelTheme.globalColors.text.normal
                )
            )
            Text(
                text = subtitle,
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 10.sp,
                    color = JewelTheme.globalColors.text.normal.copy(alpha = 0.7f)
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // 鐘舵€佹寚绀哄櫒
                Text(
            text = when (status) {
                ToolCallStatus.PENDING -> "待处理"
                ToolCallStatus.RUNNING -> "执行中"
                ToolCallStatus.SUCCESS -> "已完成"
                ToolCallStatus.FAILED -> "已失败"
                ToolCallStatus.CANCELLED -> "已取消"
            },
            style = JewelTheme.defaultTextStyle.copy(fontSize = 12.sp)
        )
    }
}

/**
 * 鏂囦欢鍐呭灞曠ず缁勪欢锛堝彲澶嶇敤锛? * Read/Write/Edit绛夋枃浠舵搷浣滃伐鍏峰彲浠ュ鐢? */
@Composable
fun FileContentDisplay(
    content: String,
    filePath: String? = null,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 鏂囦欢璺緞锛堝鏋滄彁渚涳級
        filePath?.let { path ->
            Text(
                text = "馃搫 ${path.substringAfterLast('/')}",
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = JewelTheme.globalColors.text.normal.copy(alpha = 0.8f)
                )
            )
        }

        // 鏂囦欢鍐呭
        SelectionContainer {
            Text(
                text = content,
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = JewelTheme.globalColors.text.normal.copy(alpha = 0.8f),
                    lineHeight = 16.sp  // 澧炲姞琛岄珮閬垮厤閲嶅彔锛?45%鐨勫瓧浣撳ぇ灏忥級
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (maxLines != Int.MAX_VALUE) {
                            Modifier.heightIn(max = (maxLines * 16).dp)
                        } else {
                            Modifier
                        }
                    )
                    .verticalScroll(rememberScrollState()),
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 鎼滅储缁撴灉灞曠ず缁勪欢锛堝彲澶嶇敤锛? * Glob/Grep绛夋悳绱㈠伐鍏峰彲浠ュ鐢? */
@Composable
fun SearchResultDisplay(
    results: List<String>,
    searchTerm: String? = null,
    totalCount: Int? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 鎼滅储缁熻
        val count = totalCount ?: results.size
        Text(
            text = buildString {
                append("馃攳 ")
                if (searchTerm != null) {
                    append("鎼滅储 \"$searchTerm\"锛?)
                }
                append("鎵惧埌 $count 涓粨鏋?)
            },
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = JewelTheme.globalColors.text.normal.copy(alpha = 0.8f)
            )
        )

        // 缁撴灉鍒楄〃
        if (results.isEmpty()) {
            Text(
                text = "鏈壘鍒板尮閰嶇粨鏋?,
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 10.sp,
                    color = JewelTheme.globalColors.text.normal.copy(alpha = 0.6f)
                )
            )
        } else {
            Column(
                modifier = Modifier
                    .heightIn(max = 120.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                results.take(20).forEach { result ->
                    Text(
                        text = result,
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = JewelTheme.globalColors.text.normal.copy(alpha = 0.8f)
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (results.size > 20) {
                    Text(
                        text = "... 杩樻湁 ${results.size - 20} 涓粨鏋?,
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 9.sp,
                            color = JewelTheme.globalColors.text.normal.copy(alpha = 0.6f)
                        )
                    )
                }
            }
        }
    }
}

/**
 * 缃戦〉鍐呭灞曠ず缁勪欢锛堝彲澶嶇敤锛? * WebFetch/WebSearch绛夌綉缁滃伐鍏峰彲浠ュ鐢? */
@Composable
fun WebContentDisplay(
    content: String,
    url: String? = null,
    title: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // URL鎴栨爣棰?        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "馃寪",
                style = JewelTheme.defaultTextStyle.copy(fontSize = 12.sp)
            )
            Text(
                text = title ?: url?.let {
                    it.removePrefix("https://").removePrefix("http://").substringBefore("/")
                } ?: "缃戦〉鍐呭",
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = JewelTheme.globalColors.text.normal.copy(alpha = 0.8f)
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // 鍐呭鎽樿
        SelectionContainer {
            Text(
                text = if (content.length > 300) content.take(297) + "..." else content,
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 10.sp,
                    color = JewelTheme.globalColors.text.normal.copy(alpha = 0.8f),
                    lineHeight = 15.sp  // 澧炲姞琛岄珮閬垮厤閲嶅彔锛?50%鐨勫瓧浣撳ぇ灏忥級
                ),
                modifier = Modifier
                    .heightIn(max = 80.dp)
                    .verticalScroll(rememberScrollState())
            )
        }

        // 鍐呭缁熻
        Text(
            text = "鍐呭闀垮害: ${content.length} 瀛楃",
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 9.sp,
                color = JewelTheme.globalColors.text.normal.copy(alpha = 0.5f)
            )
        )
    }
}

/**
 * 宸紓灞曠ず缁勪欢锛堝彲澶嶇敤锛? * Edit/MultiEdit绛夌紪杈戝伐鍏峰彲浠ュ鐢? */
@Composable
fun DiffDisplay(
    oldContent: String?,
    newContent: String?,
    filePath: String? = null,
    changeCount: Int? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 鏂囦欢淇℃伅
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "鉁忥笍",
                style = JewelTheme.defaultTextStyle.copy(fontSize = 12.sp)
            )
            Text(
                text = buildString {
                    append(filePath?.substringAfterLast('/') ?: "鏂囦欢缂栬緫")
                    if (changeCount != null) {
                        append(" ($changeCount 澶勪慨鏀?")
                    }
                },
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = JewelTheme.globalColors.text.normal.copy(alpha = 0.8f)
                )
            )
        }

        // 宸紓鍐呭锛堢畝鍖栨樉绀猴級
        if (oldContent != null && newContent != null) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(JewelTheme.globalColors.panelBackground.copy(alpha = 0.3f))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // 鍒犻櫎鐨勫唴瀹?                if (oldContent.isNotEmpty()) {
                    Text(
                        text = "- ${oldContent.take(100)}${if (oldContent.length > 100) "..." else ""}",
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFFF6B6B)
                        )
                    )
                }

                // 娣诲姞鐨勫唴瀹?                if (newContent.isNotEmpty()) {
                    Text(
                        text = "+ ${newContent.take(100)}${if (newContent.length > 100) "..." else ""}",
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF4CAF50)
                        )
                    )
                }
            }
        }
    }
}

/**
 * 缁堢杈撳嚭灞曠ず缁勪欢锛堝彲澶嶇敤锛? * Bash绛夊懡浠ゅ伐鍏峰彲浠ュ鐢? */
@Composable
fun TerminalOutputDisplay(
    output: String,
    command: String? = null,
    exitCode: Int? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 鍛戒护淇℃伅
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "馃捇",
                style = JewelTheme.defaultTextStyle.copy(fontSize = 12.sp)
            )
            Text(
                text = buildString {
                    append(command?.take(30) ?: "鍛戒护鎵ц")
                    if (command != null && command.length > 30) append("...")
                    if (exitCode != null) {
                        append(" (閫€鍑虹爜: $exitCode)")
                    }
                },
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = JewelTheme.globalColors.text.normal.copy(alpha = 0.8f)
                )
            )
        }

        // 杈撳嚭鍐呭
        SelectionContainer {
            Text(
                text = output,
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = JewelTheme.globalColors.text.normal.copy(alpha = 0.8f),
                    lineHeight = 12.sp
                ),
                modifier = Modifier
                    .heightIn(max = 100.dp)
                    .verticalScroll(rememberScrollState())
            )
        }
    }
}

/**
 * 閫氱敤宸ュ叿缁撴灉灞曠ず缁勪欢锛堝彲澶嶇敤锛? * 澶勭悊鍚勭ToolResult绫诲瀷
 */
@Composable
fun ToolResultDisplay(
    result: ToolResult,
    modifier: Modifier = Modifier
) {
    when (result) {
        is ToolResult.Success -> {
            Text(
                text = if (result.output.length > 200) {
                    result.output.take(197) + "..."
                } else {
                    result.output
                },
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 11.sp,
                    color = JewelTheme.globalColors.text.normal.copy(alpha = 0.8f)
                ),
                modifier = modifier
            )
        }
        is ToolResult.Failure -> {
            Text(
                text = "鉂?${result.error}",
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 11.sp,
                    color = Color(0xFFFF6B6B)
                ),
                modifier = modifier
            )
        }
        else -> {
            Text(
                text = result.toString(),
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 11.sp,
                    color = JewelTheme.globalColors.text.normal.copy(alpha = 0.6f)
                ),
                modifier = modifier
            )
        }
    }
}


