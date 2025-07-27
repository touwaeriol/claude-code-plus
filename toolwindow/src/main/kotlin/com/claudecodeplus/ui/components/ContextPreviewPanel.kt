package com.claudecodeplus.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.Orientation
import com.claudecodeplus.ui.models.*
import com.claudecodeplus.ui.services.ContextManagementService
import com.claudecodeplus.ui.services.ContextSizeInfo
import com.claudecodeplus.ui.services.ContextTemplateManager
import com.claudecodeplus.ui.services.ContextRecommendationEngine
import kotlinx.coroutines.launch
import java.io.File
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/**
 * 上下文预览面板
 */
@Composable
fun ContextPreviewPanel(
    contextItems: List<ContextItem>,
    contextManagementService: ContextManagementService,
    templateManager: ContextTemplateManager,
    recommendationEngine: ContextRecommendationEngine? = null,
    projectPath: String,
    recentMessages: List<EnhancedMessage> = emptyList(),
    onContextChange: (List<ContextItem>) -> Unit,
    onOpenFile: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedItems by remember { mutableStateOf(setOf<String>()) }
    var showAddMenu by remember { mutableStateOf(false) }
    var showTemplateDialog by remember { mutableStateOf(false) }
    var showRecommendations by remember { mutableStateOf(false) }
    var contextSize by remember { mutableStateOf<ContextSizeInfo?>(null) }
    var recommendations by remember { mutableStateOf<List<ContextSuggestion>>(emptyList()) }
    
    val scope = rememberCoroutineScope()
    
    // 计算上下文大小
    LaunchedEffect(contextItems) {
        scope.launch {
            contextSize = contextManagementService.calculateContextSize(contextItems)
        }
    }
    
    // 获取推荐
    LaunchedEffect(contextItems, recentMessages) {
        if (recommendationEngine != null) {
            scope.launch {
                recommendations = recommendationEngine.recommendContext(
                    currentContext = contextItems,
                    recentMessages = recentMessages,
                    projectPath = projectPath
                )
            }
        }
    }
    
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(JewelTheme.globalColors.panelBackground)
    ) {
        // 标题栏
        ContextPanelHeader(
            itemCount = contextItems.size,
            sizeInfo = contextSize,
            onAddClick = { showAddMenu = true },
            onTemplateClick = { showTemplateDialog = true },
            onRecommendClick = { showRecommendations = !showRecommendations }
        )
        
        Divider(orientation = Orientation.Horizontal)
        
        // 推荐区域
        if (showRecommendations && recommendations.isNotEmpty()) {
            RecommendationSection(
                recommendations = recommendations,
                onAccept = { suggestion ->
                    onContextChange(contextItems + suggestion.item)
                },
                onDismiss = { showRecommendations = false }
            )
            
            Divider(orientation = Orientation.Horizontal)
        }
        
        // 上下文列表
        Box(modifier = Modifier.weight(1f)) {
            if (contextItems.isEmpty()) {
                EmptyContextView(
                    onAddClick = { showAddMenu = true }
                )
            } else {
                ContextItemList(
                    items = contextItems,
                    expandedItems = expandedItems,
                    contextManagementService = contextManagementService,
                    onToggleExpand = { id ->
                        expandedItems = if (id in expandedItems) {
                            expandedItems - id
                        } else {
                            expandedItems + id
                        }
                    },
                    onRemove = { item ->
                        onContextChange(contextItems - item)
                    },
                    onOpenFile = onOpenFile
                )
            }
        }
        
        // 操作栏
        ContextActionBar(
            hasItems = contextItems.isNotEmpty(),
            onClearAll = { onContextChange(emptyList()) },
            onValidate = {
                scope.launch {
                    val result = contextManagementService.validateContext(contextItems)
                    if (result.invalidItems.isNotEmpty()) {
                        // TODO: 显示验证结果
                    }
                }
            }
        )
    }
    
    // 添加菜单
    if (showAddMenu) {
        AddContextMenu(
            onDismiss = { showAddMenu = false },
            onAddFile = {
                // TODO: 打开文件选择器
                showAddMenu = false
            },
            onAddFolder = {
                // TODO: 打开文件夹选择器
                showAddMenu = false
            },
            onAddFromTemplate = {
                showTemplateDialog = true
                showAddMenu = false
            }
        )
    }
    
    // 模板对话框
    if (showTemplateDialog) {
        ContextTemplateDialog(
            templateManager = templateManager,
            projectPath = projectPath,
            onApplyTemplate = { template ->
                val newItems = templateManager.applyTemplate(template, projectPath)
                onContextChange(
                    contextManagementService.mergeContextItems(contextItems, newItems)
                )
                showTemplateDialog = false
            },
            onDismiss = { showTemplateDialog = false }
        )
    }
}

/**
 * 面板标题栏
 */
@Composable
private fun ContextPanelHeader(
    itemCount: Int,
    sizeInfo: ContextSizeInfo?,
    onAddClick: () -> Unit,
    onTemplateClick: () -> Unit,
    onRecommendClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "上下文",
                style = JewelTheme.defaultTextStyle
            )
            
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = onRecommendClick,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "推荐",
                        modifier = Modifier.size(16.dp)
                    )
                }
                
                IconButton(
                    onClick = onTemplateClick,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = "模板",
                        modifier = Modifier.size(16.dp)
                    )
                }
                
                IconButton(
                    onClick = onAddClick,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "添加",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        
        if (sizeInfo != null) {
            Text(
                text = "$itemCount 项 • ${sizeInfo.formatSize()} • ~${sizeInfo.estimatedTokens} tokens",
                style = JewelTheme.defaultTextStyle,
                color = Color.Gray
            )
        }
    }
}

/**
 * 推荐区域
 */
@Composable
private fun RecommendationSection(
    recommendations: List<ContextSuggestion>,
    onAccept: (ContextSuggestion) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.LightGray.copy(alpha = 0.2f).copy(alpha = 0.1f))
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "推荐添加",
                style = JewelTheme.defaultTextStyle
            )
            
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(16.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "关闭",
                    modifier = Modifier.size(12.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        recommendations.take(3).forEach { suggestion ->
            RecommendationItem(
                suggestion = suggestion,
                onAccept = { onAccept(suggestion) }
            )
        }
    }
}

/**
 * 推荐项
 */
@Composable
private fun RecommendationItem(
    suggestion: ContextSuggestion,
    onAccept: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = when (val item = suggestion.item) {
                    is ContextItem.File -> File(item.path).name
                    is ContextItem.Folder -> File(item.path).name
                    is ContextItem.CodeBlock -> item.description ?: "代码片段"
                },
                style = JewelTheme.defaultTextStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Text(
                text = suggestion.reason,
                style = JewelTheme.defaultTextStyle,
                color = Color.Gray
            )
        }
        
        OutlinedButton(
            onClick = onAccept,
            modifier = Modifier.padding(start = 8.dp)
        ) {
            Text("添加", style = JewelTheme.defaultTextStyle)
        }
    }
}

/**
 * 上下文项列表
 */
@Composable
private fun ContextItemList(
    items: List<ContextItem>,
    expandedItems: Set<String>,
    contextManagementService: ContextManagementService,
    onToggleExpand: (String) -> Unit,
    onRemove: (ContextItem) -> Unit,
    onOpenFile: (String) -> Unit
) {
    val groupedItems = remember(items) {
        contextManagementService.groupContextItems(items)
    }
    
    LazyColumn(
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        groupedItems.forEach { (group, groupItems) ->
            item {
                Text(
                    text = group,
                    style = JewelTheme.defaultTextStyle,
                    color = Color.Gray,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            
            items(groupItems, key = { it.id }) { item ->
                ContextItemBox(
                    item = item,
                    isExpanded = item.id in expandedItems,
                    contextManagementService = contextManagementService,
                    onToggleExpand = { onToggleExpand(item.id) },
                    onRemove = { onRemove(item) },
                    onOpenFile = onOpenFile
                )
            }
        }
    }
}

/**
 * 上下文项卡片
 */
@Composable
private fun ContextItemBox(
    item: ContextItem,
    isExpanded: Boolean,
    contextManagementService: ContextManagementService,
    onToggleExpand: () -> Unit,
    onRemove: () -> Unit,
    onOpenFile: (String) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    when (item) {
                        is ContextItem.File -> onOpenFile(item.path)
                        is ContextItem.Folder -> onToggleExpand()
                        else -> {}
                    }
                },
                onLongClick = { showMenu = true }
            )
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 图标
                    val icon = when (item) {
                        is ContextItem.File -> Icons.Default.List
                        is ContextItem.Folder -> if (isExpanded) Icons.Default.List else Icons.Default.List
                        is ContextItem.CodeBlock -> Icons.Default.Info
                    }
                    
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = when (item) {
                            is ContextItem.File -> Color.Gray
                            is ContextItem.Folder -> Color(0xFFFFB74D)
                            is ContextItem.CodeBlock -> Color(0xFF4FC3F7)
                        }
                    )
                    
                    // 名称
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (item) {
                                is ContextItem.File -> File(item.path).name
                                is ContextItem.Folder -> File(item.path).name
                                is ContextItem.CodeBlock -> item.description ?: "${item.language} 代码"
                            },
                            style = JewelTheme.defaultTextStyle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        if (item is ContextItem.Folder) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                item.includePattern?.let {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color.LightGray.copy(alpha = 0.2f))
                                    ) {
                                        Text(
                                            "包含: $it",
                                            style = JewelTheme.defaultTextStyle,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                item.excludePattern?.let {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color.LightGray.copy(alpha = 0.2f))
                                    ) {
                                        Text(
                                            "排除: $it",
                                            style = JewelTheme.defaultTextStyle,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                // 操作按钮
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (item is ContextItem.Folder) {
                        IconButton(
                            onClick = onToggleExpand,
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isExpanded) "收起" else "展开",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "移除",
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
            
            // 展开的文件夹内容
            if (item is ContextItem.Folder && isExpanded) {
                var expandedFiles by remember { mutableStateOf<List<ContextItem.File>>(emptyList()) }
                
                LaunchedEffect(item) {
                    expandedFiles = contextManagementService.expandFolder(item, maxFiles = 50)
                }
                
                if (expandedFiles.isNotEmpty()) {
                    Divider(orientation = Orientation.Horizontal, modifier = Modifier.padding(vertical = 4.dp))
                    
                    Column(
                        modifier = Modifier.padding(start = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        expandedFiles.take(10).forEach { file ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenFile(file.path) }
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.List,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = Color.Gray
                                )
                                
                                Text(
                                    text = File(file.path).name,
                                    style = JewelTheme.defaultTextStyle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        
                        if (expandedFiles.size > 10) {
                            Text(
                                text = "还有 ${expandedFiles.size - 10} 个文件...",
                                style = JewelTheme.defaultTextStyle,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
            
            // 代码块预览
            if (item is ContextItem.CodeBlock) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.LightGray.copy(alpha = 0.1f))
                ) {
                    Text(
                        text = item.content.take(200) + if (item.content.length > 200) "..." else "",
                        style = JewelTheme.defaultTextStyle.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
    
    // 右键菜单 - 暂时注释掉，后续实现
    // TODO: 实现右键菜单功能
}

/**
 * 空状态视图
 */
@Composable
private fun EmptyContextView(
    onAddClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.List,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = Color.Gray.copy(alpha = 0.5f)
            )
            
            Text(
                "暂无上下文",
                style = JewelTheme.defaultTextStyle,
                color = Color.Gray
            )
            
            OutlinedButton(onClick = onAddClick) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("添加文件或文件夹")
            }
        }
    }
}

/**
 * 操作栏
 */
@Composable
private fun ContextActionBar(
    hasItems: Boolean,
    onClearAll: () -> Unit,
    onValidate: () -> Unit
) {
    if (hasItems) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedButton(onClick = onValidate) {
                Icon(Icons.Default.CheckCircle, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("验证")
            }
            
            OutlinedButton(onClick = onClearAll) {
                Icon(Icons.Default.Clear, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("清空")
            }
        }
    }
}

/**
 * 添加上下文菜单
 */
@Composable
private fun AddContextMenu(
    onDismiss: () -> Unit,
    onAddFile: () -> Unit,
    onAddFolder: () -> Unit,
    onAddFromTemplate: () -> Unit
) {
    Popup(
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White)
                .border(1.dp, Color.LightGray, RoundedCornerShape(4.dp))
        ) {
            Column(
                modifier = Modifier.width(200.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAddFile() }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.List, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("添加文件")
                }
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAddFolder() }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.List, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("添加文件夹")
                }
                
                Divider(orientation = Orientation.Horizontal)
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAddFromTemplate() }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Star, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("从模板添加")
                }
            }
        }
    }
}