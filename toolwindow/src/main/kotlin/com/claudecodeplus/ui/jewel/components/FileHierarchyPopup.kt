/*
 * FileHierarchyPopup.kt
 * 
 * 文件层级树悬浮组件 - 展示完整的文件目录结构
 * 基于Jewel组件实现，支持折叠和高亮当前文件路径
 */

package com.claudecodeplus.ui.jewel.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.claudecodeplus.ui.services.IndexedFileInfo
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

/**
 * 层级节点数据类
 */
data class HierarchyNode(
    val name: String,
    val fullPath: String,
    val isDirectory: Boolean,
    val level: Int = 0,
    val children: MutableList<HierarchyNode> = mutableListOf(),
    val isExpanded: Boolean = true,
    val isHighlighted: Boolean = false
)

/**
 * 文件层级悬浮弹窗 - 支持绝对定位
 * 
 * @param targetFile 目标文件信息
 * @param onDismiss 关闭回调
 * @param anchorBounds 主弹窗的边界信息，用于计算二级弹窗位置
 * @param modifier 修饰符
 */
@Composable
fun FileHierarchyPopup(
    targetFile: IndexedFileInfo,
    onDismiss: () -> Unit,
    anchorBounds: androidx.compose.ui.geometry.Rect? = null,
    modifier: Modifier = Modifier
) {
    // 构建层级树
    val hierarchyRoot = remember(targetFile) {
        buildHierarchyTree(targetFile)
    }
    
    // 计算弹窗位置
    val popupOffset = remember(anchorBounds) {
        if (anchorBounds != null) {
            // 显示在主弹窗右侧
            androidx.compose.ui.unit.IntOffset(
                x = (anchorBounds.right + 8).toInt(), // 主弹窗右边 + 8dp间距
                y = (anchorBounds.top - 60).toInt()   // 向上偏移以对齐选中项
            )
        } else {
            androidx.compose.ui.unit.IntOffset(380, -60) // 回退到相对定位
        }
    }
    
    Popup(
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        ),
        alignment = Alignment.TopStart,
        offset = popupOffset
    ) {
        // 使用Jewel样式的容器
        Box(
            modifier = Modifier
                .width(200.dp)  // 进一步缩小宽度
                .heightIn(max = 250.dp)  // 缩小最大高度
                .background(
                    JewelTheme.globalColors.panelBackground,
                    RoundedCornerShape(8.dp)
                )
                .border(
                    1.dp,
                    JewelTheme.globalColors.borders.normal,
                    RoundedCornerShape(8.dp)
                )
                .padding(6.dp)  // 减小内边距
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // 标题
                Text(
                    text = "File Location",
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 10.sp,  // 缩小标题字体
                        color = JewelTheme.globalColors.text.disabled
                    ),
                    modifier = Modifier.padding(bottom = 6.dp)  // 减小下边距
                )
                
                // 层级树
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(0.dp)  // 减小垂直间距
                ) {
                    hierarchyRoot?.let { root ->
                        HierarchyTreeView(
                            node = root,
                            targetPath = targetFile.absolutePath
                        )
                    }
                }
            }
        }
    }
}

/**
 * 层级树视图组件
 */
@Composable
private fun HierarchyTreeView(
    node: HierarchyNode,
    targetPath: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // 当前节点
        HierarchyNodeItem(
            node = node,
            isTarget = node.fullPath == targetPath
        )
        
        // 子节点（递归）
        if (node.isExpanded && node.children.isNotEmpty()) {
            node.children.forEach { child ->
                HierarchyTreeView(
                    node = child,
                    targetPath = targetPath,
                    modifier = Modifier.padding(start = 12.dp)  // 减小层级缩进
                )
            }
        }
    }
}

/**
 * 单个层级节点项
 */
@Composable
private fun HierarchyNodeItem(
    node: HierarchyNode,
    isTarget: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(
                if (isTarget) 
                    JewelTheme.globalColors.borders.focused.copy(alpha = 0.15f)
                else 
                    Color.Transparent
            )
            .padding(horizontal = 4.dp, vertical = 2.dp),  // 减小节点项内边距
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)  // 减小图标和文本间距
    ) {
        // 文件/文件夹图标
        Text(
            text = if (node.isDirectory) "📁" else getFileIcon(node.name),
            style = JewelTheme.defaultTextStyle.copy(fontSize = 10.sp)  // 缩小图标
        )
        
        // 名称
        Text(
            text = node.name,
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 10.sp,  // 缩小字体
                color = if (isTarget) {
                    JewelTheme.globalColors.borders.focused
                } else if (node.isDirectory) {
                    JewelTheme.globalColors.text.normal
                } else {
                    JewelTheme.globalColors.text.normal.copy(alpha = 0.8f)
                }
            )
        )
    }
}

/**
 * 构建文件层级树
 */
private fun buildHierarchyTree(targetFile: IndexedFileInfo): HierarchyNode? {
    val pathParts = targetFile.relativePath.split("/").filter { it.isNotEmpty() }
    if (pathParts.isEmpty()) return null
    
    // 创建根节点（项目根目录）
    val root = HierarchyNode(
        name = "📁 ${pathParts.first()}",
        fullPath = pathParts.first(),
        isDirectory = true,
        level = 0
    )
    
    // 构建完整路径
    var currentNode = root
    var currentPath = pathParts.first()
    
    for (i in 1 until pathParts.size) {
        val part = pathParts[i]
        val isLastPart = i == pathParts.size - 1
        currentPath += "/$part"
        
        val childNode = HierarchyNode(
            name = part,
            fullPath = currentPath,
            isDirectory = !isLastPart,
            level = i
        )
        
        currentNode.children.add(childNode)
        currentNode = childNode
    }
    
    return root
}

/**
 * 获取文件图标
 */
private fun getFileIcon(fileName: String): String {
    return when (fileName.substringAfterLast('.', "")) {
        "kt" -> "🟢"
        "java" -> "☕"
        "js", "ts" -> "💛"
        "py" -> "🐍"
        "md" -> "📝"
        "json" -> "📋"
        "xml" -> "📄"
        "gradle" -> "🐘"
        "properties" -> "⚙️"
        else -> "📄"
    }
}