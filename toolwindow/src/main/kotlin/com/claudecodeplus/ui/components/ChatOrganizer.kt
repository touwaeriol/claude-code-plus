package com.claudecodeplus.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.Orientation
import com.claudecodeplus.ui.models.*
import com.claudecodeplus.ui.services.ChatTabManager
import kotlinx.coroutines.launch
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import java.time.format.DateTimeFormatter
import java.time.ZoneId

/**
 * 对话组织管理器
 */
@Composable
fun ChatOrganizer(
    tabManager: ChatTabManager,
    onTabSelect: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedGroupId by remember { mutableStateOf<String?>("default") }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showManageTagsDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    
    Row(modifier = modifier) {
        // 左侧：分组列表
        GroupSidebar(
            groups = tabManager.groups,
            selectedGroupId = selectedGroupId,
            onGroupSelect = { selectedGroupId = it },
            onGroupCreate = { showCreateGroupDialog = true },
            onGroupEdit = { /* TODO */ },
            modifier = Modifier.width(200.dp)
        )
        
        Divider(orientation = Orientation.Vertical)
        
        // 右侧：对话列表和操作
        Column(modifier = Modifier.weight(1f)) {
            // 工具栏
            OrganizerToolbar(
                searchQuery = searchQuery,
                onSearchChange = { searchQuery = it },
                onManageTags = { showManageTagsDialog = true },
                onClose = onClose
            )
            
            Divider(orientation = Orientation.Horizontal)
            
            // 对话列表
            val filteredTabs = filterTabs(
                tabs = tabManager.tabs,
                groupId = selectedGroupId,
                searchQuery = searchQuery
            )
            
            ChatTabList(
                tabs = filteredTabs,
                groups = tabManager.groups,
                onTabSelect = onTabSelect,
                onTabMove = { tabId, groupId ->
                    tabManager.moveTabToGroup(tabId, groupId)
                },
                onTabDelete = { tabManager.closeTab(it, force = true) },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
    
    // 创建分组对话框
    if (showCreateGroupDialog) {
        CreateGroupDialog(
            onConfirm = { name, color ->
                tabManager.createGroup(name, color)
                showCreateGroupDialog = false
            },
            onDismiss = { showCreateGroupDialog = false }
        )
    }
    
    // 管理标签对话框
    if (showManageTagsDialog) {
        ManageTagsDialog(
            onDismiss = { showManageTagsDialog = false }
        )
    }
}

/**
 * 分组侧边栏
 */
@Composable
private fun GroupSidebar(
    groups: List<ChatGroup>,
    selectedGroupId: String?,
    onGroupSelect: (String?) -> Unit,
    onGroupCreate: () -> Unit,
    onGroupEdit: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(JewelTheme.globalColors.panelBackground)
            .padding(8.dp)
    ) {
        // 标题
        Text(
            text = "分组",
            style = JewelTheme.defaultTextStyle,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 全部对话
        GroupItem(
            name = "全部对话",
            icon = AllIconsKeys.Actions.ListFiles,
            isSelected = selectedGroupId == null,
            onClick = { onGroupSelect(null) }
        )
        
        Divider(orientation = Orientation.Horizontal, modifier = Modifier.padding(vertical = 8.dp))
        
        // 分组列表
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(groups) { group ->
                GroupItem(
                    name = group.name,
                    color = group.color,
                    isSelected = selectedGroupId == group.id,
                    onClick = { onGroupSelect(group.id) },
                    onEdit = { onGroupEdit(group.id) }
                )
            }
        }
        
        // 创建分组按钮
        OutlinedButton(
            onClick = onGroupCreate,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(AllIconsKeys.General.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(4.dp))
            Text("新建分组")
        }
    }
}

/**
 * 分组项
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun GroupItem(
    name: String,
    color: Color? = null,
    icon: org.jetbrains.jewel.ui.icon.IconKey? = null,
    isSelected: Boolean,
    onClick: () -> Unit,
    onEdit: (() -> Unit)? = null
) {
    var isHovered by remember { mutableStateOf(false) }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(
                if (isSelected) Color(0xFF2675BF).copy(alpha = 0.3f)
                else if (isHovered) Color.Gray.copy(alpha = 0.1f)
                else Color.Transparent
            )
            .clickable { onClick() }
            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 颜色指示器或图标
        when {
            icon != null -> Icon(
                key = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            color != null -> Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(color, RoundedCornerShape(2.dp))
            )
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Text(
            text = name,
            style = JewelTheme.defaultTextStyle,
            modifier = Modifier.weight(1f)
        )
        
        // 编辑按钮
        if (onEdit != null && isHovered) {
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    AllIconsKeys.Actions.Edit,
                    contentDescription = "编辑",
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

/**
 * 工具栏
 */
@Composable
private fun OrganizerToolbar(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onManageTags: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 搜索框 - 使用简单的文本输入
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                AllIconsKeys.Actions.Find,
                contentDescription = null,
                modifier = Modifier.size(16.dp).padding(end = 4.dp)
            )
            // 暂时使用Text作为占位，后续实现搜索功能
            Text(
                text = if (searchQuery.isEmpty()) "搜索对话..." else searchQuery,
                color = if (searchQuery.isEmpty()) Color.Gray else JewelTheme.defaultTextStyle.color,
                modifier = Modifier.weight(1f)
            )
        }
        
        // 操作按钮
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick = onManageTags) {
                Icon(AllIconsKeys.Nodes.Bookmark, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("管理标签")
            }
            
            IconButton(onClick = onClose) {
                Icon(AllIconsKeys.Actions.Close, contentDescription = "关闭")
            }
        }
    }
}

/**
 * 对话列表
 */
@Composable
private fun ChatTabList(
    tabs: List<ChatTab>,
    groups: List<ChatGroup>,
    onTabSelect: (String) -> Unit,
    onTabMove: (tabId: String, groupId: String) -> Unit,
    onTabDelete: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(tabs, key = { it.id }) { tab ->
            ChatTabSurface(
                tab = tab,
                group = groups.find { it.id == tab.groupId },
                onSelect = { onTabSelect(tab.id) },
                onMove = { groupId -> onTabMove(tab.id, groupId) },
                onDelete = { onTabDelete(tab.id) }
            )
        }
    }
}

/**
 * 对话卡片
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatTabSurface(
    tab: ChatTab,
    group: ChatGroup?,
    onSelect: () -> Unit,
    onMove: (String) -> Unit,
    onDelete: () -> Unit
) {
    var showContextMenu by remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onSelect,
                onLongClick = { showContextMenu = true }
            )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // 标题行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = tab.title,
                    style = JewelTheme.defaultTextStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                
                Text(
                    text = formatTime(tab.lastModified),
                    style = JewelTheme.defaultTextStyle,
                    color = Color.Gray
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // 摘要或最后消息
            tab.summary?.let { summary ->
                Text(
                    text = summary,
                    style = JewelTheme.defaultTextStyle,
                    color = Color.Gray,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            } ?: tab.messages.lastOrNull()?.let { lastMessage ->
                Text(
                    text = lastMessage.content,
                    style = JewelTheme.defaultTextStyle,
                    color = Color.Gray,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 标签和分组
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 分组标签
                group?.let {
                    Chip(
                        onClick = {},
                        enabled = false
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(it.color, RoundedCornerShape(1.dp))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(it.name, style = JewelTheme.defaultTextStyle)
                    }
                }
                
                // 其他标签
                tab.tags.forEach { tag ->
                    Chip(
                        onClick = {},
                        enabled = false
                    ) {
                        Text(tag.name, style = JewelTheme.defaultTextStyle)
                    }
                }
            }
        }
    }
    
    // 右键菜单 - 暂时注释掉，后续实现
    // TODO: 实现右键菜单功能
}

/**
 * 创建分组对话框
 */
@Composable
private fun CreateGroupDialog(
    onConfirm: (name: String, color: Color) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(Color(0xFF4CAF50)) }
    
    val presetColors = listOf(
        Color(0xFF4CAF50),
        Color(0xFF2196F3),
        Color(0xFFFF9800),
        Color(0xFFE91E63),
        Color(0xFF9C27B0),
        Color(0xFF00BCD4),
        Color(0xFFFFEB3B),
        Color(0xFF795548)
    )
    
    DialogWindow(
        onCloseRequest = onDismiss,
        title = "创建分组"
    ) {
        Column(
            modifier = Modifier
                .width(300.dp)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 分组名称
            Column {
                Text("分组名称", style = JewelTheme.defaultTextStyle)
                Spacer(modifier = Modifier.height(4.dp))
                // 暂时使用文本显示，后续实现输入功能
                Text(
                    text = if (name.isEmpty()) "请输入分组名称" else name,
                    color = if (name.isEmpty()) Color.Gray else JewelTheme.defaultTextStyle.color,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            // 颜色选择
            Column {
                Text("选择颜色", style = JewelTheme.defaultTextStyle)
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presetColors.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(color, RoundedCornerShape(4.dp))
                                .border(
                                    width = if (color == selectedColor) 2.dp else 0.dp,
                                    color = Color.Black,
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .clickable { selectedColor = color }
                        )
                    }
                }
            }
            
            // 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = onDismiss) {
                    Text("取消")
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                DefaultButton(
                    onClick = {
                        if (name.isNotBlank()) {
                            onConfirm(name, selectedColor)
                        }
                    },
                    enabled = name.isNotBlank()
                ) {
                    Text("创建")
                }
            }
        }
    }
}

/**
 * 管理标签对话框
 */
@Composable
private fun ManageTagsDialog(
    onDismiss: () -> Unit
) {
    // TODO: 实现标签管理界面
    DialogWindow(
        onCloseRequest = onDismiss,
        title = "管理标签"
    ) {
        Box(
            modifier = Modifier
                .size(400.dp, 300.dp)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("标签管理功能即将推出")
        }
    }
}

/**
 * 过滤标签
 */
private fun filterTabs(
    tabs: List<ChatTab>,
    groupId: String?,
    searchQuery: String
): List<ChatTab> {
    return tabs.filter { tab ->
        // 按分组过滤
        val matchesGroup = groupId == null || tab.groupId == groupId
        
        // 按搜索词过滤
        val matchesSearch = searchQuery.isBlank() || 
            tab.title.contains(searchQuery, ignoreCase = true) ||
            tab.messages.any { it.content.contains(searchQuery, ignoreCase = true) } ||
            tab.tags.any { it.name.contains(searchQuery, ignoreCase = true) }
        
        matchesGroup && matchesSearch
    }
}

/**
 * 格式化时间
 */
private fun formatTime(instant: java.time.Instant): String {
    val formatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())
    return formatter.format(instant)
}