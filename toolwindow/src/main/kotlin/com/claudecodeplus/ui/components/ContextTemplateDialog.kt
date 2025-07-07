package com.claudecodeplus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.Orientation
import com.claudecodeplus.ui.models.ContextTemplate
import com.claudecodeplus.ui.services.ContextTemplateManager
import kotlinx.coroutines.launch

/**
 * 上下文模板选择对话框
 */
@Composable
fun ContextTemplateDialog(
    templateManager: ContextTemplateManager,
    projectPath: String,
    onApplyTemplate: (ContextTemplate) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf("全部") }
    var searchQuery by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }
    
    val templates by templateManager.templates.collectAsState()
    val categories = remember(templates) {
        listOf("全部") + templateManager.getCategories()
    }
    
    val filteredTemplates = remember(templates, selectedCategory, searchQuery) {
        val categoryFiltered = if (selectedCategory == "全部") {
            templates
        } else {
            templates.filter { it.category == selectedCategory }
        }
        
        if (searchQuery.isBlank()) {
            categoryFiltered
        } else {
            templateManager.searchTemplates(searchQuery)
                .filter { it in categoryFiltered }
        }
    }
    
    val recommendedTemplates = remember(projectPath) {
        templateManager.getRecommendedTemplates(projectPath, limit = 3)
    }
    
    DialogWindow(
        onCloseRequest = onDismiss,
        title = "选择上下文模板"
    ) {
        Column(
            modifier = Modifier
                .size(800.dp, 600.dp)
                .padding(16.dp)
        ) {
            // 搜索和筛选栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 搜索框 - 暂时使用简单实现
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.Gray
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (searchQuery.isEmpty()) "搜索模板..." else searchQuery,
                        color = if (searchQuery.isEmpty()) Color.Gray else JewelTheme.defaultTextStyle.color
                    )
                }
                
                // 分类选择 - 暂时使用按钮
                OutlinedButton(onClick = { /* TODO: 实现分类选择 */ }) {
                    Text(selectedCategory)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                
                // 创建模板按钮
                DefaultButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("创建")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 推荐模板
            if (recommendedTemplates.isNotEmpty() && searchQuery.isBlank()) {
                Column {
                    Text(
                        "推荐模板",
                        style = JewelTheme.defaultTextStyle
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        recommendedTemplates.forEach { template ->
                            RecommendedTemplateCard(
                                template = template,
                                onClick = { onApplyTemplate(template) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(orientation = Orientation.Horizontal)
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
            
            // 模板网格
            Box(modifier = Modifier.weight(1f)) {
                if (filteredTemplates.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = Color.Gray
                            )
                            Text(
                                "未找到匹配的模板",
                                style = JewelTheme.defaultTextStyle,
                                color = Color.Gray
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(200.dp),
                        contentPadding = PaddingValues(4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredTemplates, key = { it.id }) { template ->
                            TemplateCard(
                                template = template,
                                onClick = { onApplyTemplate(template) },
                                onEdit = if (!template.isBuiltIn) {
                                    { /* TODO: 编辑模板 */ }
                                } else null,
                                onDelete = if (!template.isBuiltIn) {
                                    { templateManager.deleteTemplate(template.id) }
                                } else null
                            )
                        }
                    }
                }
            }
            
            // 底部操作栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(onClick = onDismiss) {
                    Text("取消")
                }
            }
        }
    }
    
    // 创建模板对话框
    if (showCreateDialog) {
        CreateTemplateDialog(
            onConfirm = { name, description, category ->
                // TODO: 实现创建模板
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false }
        )
    }
}

/**
 * 推荐模板卡片
 */
@Composable
private fun RecommendedTemplateCard(
    template: ContextTemplate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(80.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.LightGray.copy(alpha = 0.1f))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF2675BF).copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = template.icon ?: "📄",
                    style = JewelTheme.defaultTextStyle
                )
            }
            
            // 信息
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = template.name,
                    style = JewelTheme.defaultTextStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Text(
                    text = "${template.items.size} 项",
                    style = JewelTheme.defaultTextStyle,
                    color = Color.Gray
                )
            }
            
            // 推荐标记
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.LightGray.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("推荐", style = JewelTheme.defaultTextStyle)
                }
            }
        }
    }
}

/**
 * 模板卡片
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TemplateCard(
    template: ContextTemplate,
    onClick: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = if (!template.isBuiltIn) {{ showMenu = true }} else null
            )
            .background(Color.LightGray.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            // 头部
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // 图标
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF2675BF).copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = template.icon ?: "📄",
                        style = JewelTheme.defaultTextStyle
                    )
                }
                
                // 菜单按钮
                if (!template.isBuiltIn) {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "更多",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 标题
            Text(
                text = template.name,
                style = JewelTheme.defaultTextStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            // 描述
            Text(
                text = template.description,
                style = JewelTheme.defaultTextStyle,
                color = Color.Gray,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            
            // 底部信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                // 项目数量
                Text(
                    text = "${template.items.size} 项",
                    style = JewelTheme.defaultTextStyle,
                    color = Color.Gray
                )
                
                // 标签
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (template.isBuiltIn) {
                        Box(
                            modifier = Modifier
                                .height(20.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.LightGray.copy(alpha = 0.2f))
                        ) {
                            Text(
                                "内置",
                                style = JewelTheme.defaultTextStyle,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    
                    if (template.usageCount > 10) {
                        Box(
                            modifier = Modifier
                                .height(20.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFFF9800).copy(alpha = 0.2f))
                        ) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(12.dp)
                                    .padding(4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
    
    // 右键菜单 - 暂时注释掉，后续实现
    // TODO: 实现右键菜单功能
}

/**
 * 创建模板对话框
 */
@Composable
private fun CreateTemplateDialog(
    onConfirm: (name: String, description: String, category: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("自定义") }
    
    DialogWindow(
        onCloseRequest = onDismiss,
        title = "创建上下文模板"
    ) {
        Column(
            modifier = Modifier
                .width(400.dp)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 模板名称输入
            Column {
                Text("模板名称", style = JewelTheme.defaultTextStyle)
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = if (name.isEmpty()) "请输入模板名称" else name,
                        color = if (name.isEmpty()) Color.Gray else JewelTheme.defaultTextStyle.color
                    )
                }
            }
            
            // 描述输入
            Column {
                Text("描述", style = JewelTheme.defaultTextStyle)
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = if (description.isEmpty()) "请输入描述" else description,
                        color = if (description.isEmpty()) Color.Gray else JewelTheme.defaultTextStyle.color
                    )
                }
            }
            
            // 分类输入
            Column {
                Text("分类", style = JewelTheme.defaultTextStyle)
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = category,
                        color = JewelTheme.defaultTextStyle.color
                    )
                }
            }
            
            Text(
                "提示：创建模板后，您可以从当前上下文生成模板内容",
                style = JewelTheme.defaultTextStyle,
                color = Color.Gray
            )
            
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
                        if (name.isNotBlank() && description.isNotBlank()) {
                            onConfirm(name, description, category)
                        }
                    },
                    enabled = name.isNotBlank() && description.isNotBlank()
                ) {
                    Text("创建")
                }
            }
        }
    }
}