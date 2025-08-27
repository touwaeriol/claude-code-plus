package com.claudecodeplus.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.material.Surface
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.component.TabData.Default as TabDefault
import org.jetbrains.jewel.ui.Orientation
import com.claudecodeplus.ui.models.*
import com.claudecodeplus.ui.services.QuestionQueueManager
import kotlinx.coroutines.launch

/**
 * 批量问题对话框
 */
@Composable
fun BatchQuestionDialog(
    queueManager: QuestionQueueManager,
    currentContext: List<ContextItem>,
    sessionId: String?,
    onDismiss: () -> Unit
) {
    var questions by remember { mutableStateOf(listOf<String>()) }
    var newQuestion by remember { mutableStateOf("") }
    var useCurrentContext by remember { mutableStateOf(true) }
    var showResults by remember { mutableStateOf(false) }
    
    val queue by queueManager.queue.collectAsState()
    val isProcessing by queueManager.isProcessing.collectAsState()
    val currentQuestion by queueManager.currentQuestion.collectAsState()
    val progress by queueManager.progress.collectAsState()
    
    val scope = rememberCoroutineScope()
    
    DialogWindow(
        onCloseRequest = onDismiss,
        title = "批量提问"
    ) {
        Column(
            modifier = Modifier
                .size(900.dp, 700.dp)
                .padding(16.dp)
        ) {
            // 标签切换
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                OutlinedButton(
                    onClick = { showResults = false },
                    enabled = showResults
                ) {
                    Text("问题编辑")
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { showResults = true },
                    enabled = !showResults
                ) {
                    Text("处理结果")
                }
            }
            
            Divider(orientation = Orientation.Horizontal)
            
            Box(modifier = Modifier.weight(1f)) {
                if (!showResults) {
                    // 问题编辑视图
                    QuestionEditView(
                        questions = questions,
                        newQuestion = newQuestion,
                        useCurrentContext = useCurrentContext,
                        currentContextSize = currentContext.size,
                        onNewQuestionChange = { newQuestion = it },
                        onAddQuestion = {
                            if (newQuestion.isNotBlank()) {
                                questions = questions + newQuestion
                                newQuestion = ""
                            }
                        },
                        onUpdateQuestion = { index, value ->
                            questions = questions.toMutableList().apply {
                                this[index] = value
                            }
                        },
                        onRemoveQuestion = { index ->
                            questions = questions.filterIndexed { i, _ -> i != index }
                        },
                        onReorderQuestions = { from, to ->
                            questions = questions.toMutableList().apply {
                                add(to, removeAt(from))
                            }
                        },
                        onUseContextChange = { useCurrentContext = it },
                        onImportFromFile = {
                            // TODO: 实现从文件导入
                        }
                    )
                } else {
                    // 处理结果视图
                    QueueResultsView(
                        queue = queue,
                        currentQuestion = currentQuestion,
                        onRetryFailed = { queueManager.retryFailed() },
                        onExportResults = {
                            val results = queueManager.exportResults()
                            // TODO: 保存到文件
                        }
                    )
                }
            }
            
            // 进度条
            if (queue.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "进度: ${progress.completed}/${progress.total}",
                            style = JewelTheme.defaultTextStyle
                        )
                        
                        Text(
                            "${(progress.percentage * 100).toInt()}%",
                            style = JewelTheme.defaultTextStyle
                        )
                    }
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.LightGray)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress.percentage)
                                .background(Color(0xFF2196F3))
                        )
                    }
                }
            }
            
            Divider(orientation = Orientation.Horizontal)
            
            // 操作按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 左侧统计
                if (queue.isNotEmpty()) {
                    val stats = queueManager.getStatistics()
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Chip(onClick = {}) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("${stats.completed}", style = JewelTheme.defaultTextStyle)
                        }
                        
                        if (stats.failed > 0) {
                            Chip(onClick = {}) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = Color.Red
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("${stats.failed}", style = JewelTheme.defaultTextStyle)
                            }
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }
                
                // 右侧操作
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isProcessing) {
                        OutlinedButton(onClick = { queueManager.pauseProcessing() }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("暂停")
                        }
                    }
                    
                    if (queue.isNotEmpty() && queue.any { it.status == QueuedQuestion.QuestionStatus.COMPLETED }) {
                        OutlinedButton(onClick = { queueManager.stopAndClear() }) {
                            Text("清空")
                        }
                    }
                    
                    OutlinedButton(onClick = onDismiss) {
                        Text("关闭")
                    }
                    
                    DefaultButton(
                        onClick = {
                            if (!isProcessing && questions.isNotEmpty()) {
                                // 添加问题到队列
                                val context = if (useCurrentContext) currentContext else emptyList()
                                queueManager.addQuestions(
                                    questions.map { it to context }
                                )
                                
                                // 开始处理
                                scope.launch {
                                    queueManager.startProcessing(sessionId)
                                }
                                
                                // 切换到结果视图
                                showResults = true
                            }
                        },
                        enabled = !isProcessing && (questions.isNotEmpty() || queue.any { 
                            it.status == QueuedQuestion.QuestionStatus.PENDING 
                        })
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isProcessing) "处理中..." else "开始处理")
                    }
                }
            }
        }
    }
}

/**
 * 问题编辑视图
 */
@Composable
private fun QuestionEditView(
    questions: List<String>,
    newQuestion: String,
    useCurrentContext: Boolean,
    currentContextSize: Int,
    onNewQuestionChange: (String) -> Unit,
    onAddQuestion: () -> Unit,
    onUpdateQuestion: (Int, String) -> Unit,
    onRemoveQuestion: (Int) -> Unit,
    onReorderQuestions: (Int, Int) -> Unit,
    onUseContextChange: (Boolean) -> Unit,
    onImportFromFile: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // 工具栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CheckboxRow(
                    checked = useCurrentContext,
                    onCheckedChange = onUseContextChange
                ) {
                    Text("使用当前上下文 ($currentContextSize 项)")
                }
            }
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onImportFromFile) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("导入")
                }
                
                OutlinedButton(
                    onClick = {
                        // TODO: 显示模板
                    }
                ) {
                    Icon(Icons.Default.Star, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("模板")
                }
            }
        }
        
        // 新问题输入
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFF5F5F5))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                BasicTextField(
                    value = newQuestion,
                    onValueChange = onNewQuestionChange,
                    singleLine = true,
                    textStyle = JewelTheme.defaultTextStyle.copy(
                        color = JewelTheme.globalColors.text.normal
                    ),
                    cursorBrush = SolidColor(JewelTheme.globalColors.text.normal),
                    modifier = Modifier.fillMaxWidth()
                )
                if (newQuestion.isEmpty()) {
                    Text(
                        "输入新问题...",
                        style = JewelTheme.defaultTextStyle,
                        color = Color.Gray
                    )
                }
            }
            
            DefaultButton(
                onClick = onAddQuestion,
                enabled = newQuestion.isNotBlank()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("添加")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 问题列表
        if (questions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = Color.Gray.copy(alpha = 0.5f)
                    )
                    
                    Text(
                        "暂无问题",
                        style = JewelTheme.defaultTextStyle,
                        color = Color.Gray
                    )
                    
                    Text(
                        "添加您想批量询问的问题",
                        style = JewelTheme.defaultTextStyle,
                        color = Color.Gray
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                itemsIndexed(questions, key = { index, _ -> index }) { index, question ->
                    QuestionItem(
                        index = index,
                        question = question,
                        onUpdate = { onUpdateQuestion(index, it) },
                        onRemove = { onRemoveQuestion(index) },
                        onMoveUp = if (index > 0) {{ onReorderQuestions(index, index - 1) }} else null,
                        onMoveDown = if (index < questions.size - 1) {{ onReorderQuestions(index, index + 1) }} else null
                    )
                }
            }
        }
    }
}

/**
 * 问题项
 */
@Composable
private fun QuestionItem(
    index: Int,
    question: String,
    onUpdate: (String) -> Unit,
    onRemove: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?
) {
    var isEditing by remember { mutableStateOf(false) }
    var editText by remember(question) { mutableStateOf(question) }
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp)),
        color = Color.White
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 序号
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.LightGray),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "${index + 1}",
                    style = JewelTheme.defaultTextStyle
                )
            }
            
            // 问题内容
            Box(modifier = Modifier.weight(1f)) {
                if (isEditing) {
                    BasicTextField(
                        value = editText,
                        onValueChange = { editText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFF5F5F5))
                            .padding(8.dp),
                        textStyle = JewelTheme.defaultTextStyle.copy(
                            color = JewelTheme.globalColors.text.normal
                        ),
                        cursorBrush = SolidColor(JewelTheme.globalColors.text.normal),
                        singleLine = false
                    )
                } else {
                    Text(
                        text = question,
                        style = JewelTheme.defaultTextStyle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isEditing = true }
                    )
                }
            }
            
            // 操作按钮
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (isEditing) {
                    IconButton(
                        onClick = {
                            onUpdate(editText)
                            isEditing = false
                        },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "保存",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    
                    IconButton(
                        onClick = {
                            editText = question
                            isEditing = false
                        },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "取消",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    onMoveUp?.let {
                        IconButton(
                            onClick = it,
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowUp,
                                contentDescription = "上移",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    
                    onMoveDown?.let {
                        IconButton(
                            onClick = it,
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = "下移",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    
                    IconButton(
                        onClick = { isEditing = true },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "编辑",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "删除",
                            modifier = Modifier.size(16.dp),
                            tint = Color.Red
                        )
                    }
                }
            }
        }
    }
}

/**
 * 队列结果视图
 */
@Composable
private fun QueueResultsView(
    queue: List<QueuedQuestion>,
    currentQuestion: QueuedQuestion?,
    onRetryFailed: () -> Unit,
    onExportResults: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // 操作栏
        if (queue.any { it.status == QueuedQuestion.QuestionStatus.FAILED }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(onClick = onRetryFailed) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("重试失败")
                }
                
                OutlinedButton(onClick = onExportResults) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("导出结果")
                }
            }
        }
        
        // 结果列表
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            itemsIndexed(queue, key = { _, item -> item.id }) { index, question ->
                QueueResultItem(
                    index = index,
                    question = question,
                    isCurrent = currentQuestion?.id == question.id
                )
            }
        }
    }
}

/**
 * 队列结果项
 */
@Composable
private fun QueueResultItem(
    index: Int,
    question: QueuedQuestion,
    isCurrent: Boolean
) {
    var isExpanded by remember { mutableStateOf(false) }
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clip(RoundedCornerShape(8.dp)),
        color = if (isCurrent) {
            Color.LightGray
        } else {
            Color.White
        }
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // 头部
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 状态图标
                    val iconAndColor = when (question.status) {
                        QueuedQuestion.QuestionStatus.PENDING -> Icons.Default.Info to Color.Gray
                        QueuedQuestion.QuestionStatus.PROCESSING -> Icons.Default.Refresh to Color(0xFF2196F3)
                        QueuedQuestion.QuestionStatus.COMPLETED -> Icons.Default.CheckCircle to Color(0xFF4CAF50)
                        QueuedQuestion.QuestionStatus.FAILED -> Icons.Default.Warning to Color.Red
                        QueuedQuestion.QuestionStatus.CANCELLED -> Icons.Default.Clear to Color(0xFFFF9800)
                    }
                    val icon = iconAndColor.first
                    val color = iconAndColor.second
                    
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = color
                    )
                    
                    // 问题预览
                    Text(
                        text = "问题 ${index + 1}: ${question.content.take(50)}${if (question.content.length > 50) "..." else ""}",
                        style = JewelTheme.defaultTextStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                
                Icon(
                    if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null
                )
            }
            
            // 展开内容
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    // 完整问题
                    Text(
                        "问题：",
                        style = JewelTheme.defaultTextStyle
                    )
                    
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFF5F5F5)
                    ) {
                        Text(
                            text = question.content,
                            style = JewelTheme.defaultTextStyle,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    
                    // 结果或错误
                    when (question.status) {
                        QueuedQuestion.QuestionStatus.COMPLETED -> {
                            Text(
                                "回答：",
                                style = JewelTheme.defaultTextStyle,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFF5F5F5)
                            ) {
                                Text(
                                    text = question.result ?: "无结果",
                                    style = JewelTheme.defaultTextStyle,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                        
                        QueuedQuestion.QuestionStatus.FAILED -> {
                            Text(
                                "错误：${question.error}",
                                style = JewelTheme.defaultTextStyle,
                                color = Color.Red,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        
                        else -> {}
                    }
                }
            }
        }
    }
}