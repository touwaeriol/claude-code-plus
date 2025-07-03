/*
 * UnifiedInputArea.kt
 * 
 * 统一的输入区域组件 - 支持输入模式和显示模式
 * 确保输入框和用户消息显示的完全视觉一致性
 */

package com.claudecodeplus.ui.jewel.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudecodeplus.ui.jewel.components.context.*
import com.claudecodeplus.ui.models.*
import com.claudecodeplus.ui.services.FileIndexService
import com.claudecodeplus.ui.services.ProjectService
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 输入区域模式
 */
enum class InputAreaMode {
    INPUT,   // 输入模式：可编辑，显示所有交互元素
    DISPLAY  // 显示模式：只读，隐藏交互元素
}

/**
 * 统一的输入区域组件
 * 
 * @param mode 模式：INPUT(输入) 或 DISPLAY(显示)
 * @param value 当前文本内容
 * @param onValueChange 文本变化回调（仅INPUT模式）
 * @param onSend 发送消息回调（仅INPUT模式）
 * @param onStop 停止生成回调（仅INPUT模式）
 * @param contexts 上下文列表
 * @param onContextAdd 添加上下文回调（仅INPUT模式）
 * @param onContextRemove 移除上下文回调（仅INPUT模式）
 * @param onImageSelected 图片选择回调（仅INPUT模式）
 * @param isGenerating 是否正在生成（仅INPUT模式）
 * @param enabled 是否启用（仅INPUT模式）
 * @param selectedModel 当前选择的模型
 * @param onModelChange 模型变化回调（仅INPUT模式）
 * @param fileIndexService 文件索引服务（仅INPUT模式）
 * @param projectService 项目服务（仅INPUT模式）
 * @param inlineReferenceManager 内联引用管理器（仅INPUT模式）
 * @param message 消息对象（仅DISPLAY模式，用于显示时间戳等）
 * @param modifier 修饰符
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UnifiedInputArea(
    mode: InputAreaMode,
    value: TextFieldValue = TextFieldValue(""),
    onValueChange: (TextFieldValue) -> Unit = {},
    onSend: () -> Unit = {},
    onStop: (() -> Unit)? = null,
    contexts: List<ContextReference> = emptyList(),
    onContextAdd: (ContextReference) -> Unit = {},
    onContextRemove: (ContextReference) -> Unit = {},
    onImageSelected: (File) -> Unit = {},
    isGenerating: Boolean = false,
    enabled: Boolean = true,
    selectedModel: AiModel = AiModel.OPUS,
    onModelChange: (AiModel) -> Unit = {},
    fileIndexService: FileIndexService? = null,
    projectService: ProjectService? = null,
    inlineReferenceManager: InlineReferenceManager = remember { InlineReferenceManager() },
    message: EnhancedMessage? = null,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    var showContextSelector by remember { 
        mutableStateOf(false).also { 
            println("DEBUG: UnifiedInputArea - showContextSelector initialized to false")
        }
    }
    var atSymbolPosition by remember { mutableStateOf<Int?>(null) }
    
    // 处理@符号触发的上下文选择
    fun handleAtTriggerContext(context: ContextReference, position: Int) {
        // 生成内联引用格式
        val contextText = generateInlineReference(context)
        
        // 替换@符号位置的文本，确保索引在有效范围内
        val newText = if (position < value.text.length) {
            value.text.substring(0, position) + contextText + 
            value.text.substring(position + 1)
        } else {
            // position等于text.length，说明@在最后
            value.text.substring(0, position) + contextText
        }
        val newCursor = position + contextText.length
        
        onValueChange(
            TextFieldValue(
                text = newText,
                selection = TextRange(newCursor)
            )
        )
        
        // 重置@符号位置
        atSymbolPosition = null
    }
    
    // 整个输入区域使用统一边框和背景
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                JewelTheme.globalColors.panelBackground,
                RoundedCornerShape(8.dp)
            )
            .border(
                1.dp,
                JewelTheme.globalColors.borders.normal,
                RoundedCornerShape(8.dp)
            )
            .clip(RoundedCornerShape(8.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 第一行：上下文标签区域
        val shouldShowContextRow = contexts.isNotEmpty() || mode == InputAreaMode.INPUT
        if (shouldShowContextRow) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 仅在输入模式下显示 Add Context 按钮
                if (mode == InputAreaMode.INPUT) {
                    AddContextButton(
                        onClick = { 
                            println("DEBUG: AddContextButton clicked - setting showContextSelector to true")
                            showContextSelector = true
                            atSymbolPosition = null
                        },
                        enabled = enabled && !isGenerating,
                        modifier = Modifier.height(20.dp)
                    )
                }
                
                // 上下文标签列表
                if (contexts.isNotEmpty()) {
                    contexts.forEach { context ->
                        when (mode) {
                            InputAreaMode.INPUT -> {
                                // 输入模式：可删除的标签
                                ContextTag(
                                    context = context,
                                    onRemove = { onContextRemove(context) }
                                )
                            }
                            InputAreaMode.DISPLAY -> {
                                // 显示模式：只读标签
                                ReadOnlyContextTag(
                                    context = context,
                                    modifier = Modifier.height(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // 第二行：文本内容区域
        when (mode) {
            InputAreaMode.INPUT -> {
                // 输入模式：使用可编辑的输入框
                val contextSearchService = remember(fileIndexService, projectService) {
                    ChatInputContextSearchService(fileIndexService, projectService)
                }
                
                ChatInputField(
                    value = value,
                    onValueChange = onValueChange,
                    onSend = onSend,
                    onContextAdd = onContextAdd,
                    enabled = enabled && !isGenerating,
                    searchService = contextSearchService,
                    inlineReferenceManager = inlineReferenceManager,
                    focusRequester = focusRequester,
                    showContextSelector = showContextSelector,
                    onShowContextSelectorChange = { show ->
                        println("DEBUG: UnifiedInputArea - onShowContextSelectorChange called with: $show")
                        showContextSelector = show
                    },
                    onShowContextSelectorRequest = { position ->
                        println("DEBUG: onShowContextSelectorRequest called with position: $position")
                        showContextSelector = true
                        atSymbolPosition = position
                    },
                    onAtTriggerContext = { context, position ->
                        // 处理@符号触发的上下文
                        handleAtTriggerContext(context, position)
                    }
                )
            }
            InputAreaMode.DISPLAY -> {
                // 显示模式：使用富文本显示内联引用
                if (value.text.isNotBlank()) {
                    ClickableInlineText(
                        text = value.text,
                        onReferenceClick = { reference ->
                            // 在显示模式下可以点击引用查看详情
                            println("Reference clicked: ${reference.fullPath}")
                        },
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = JewelTheme.globalColors.text.normal
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        
        // 第三行：底部工具栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：模型信息
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                when (mode) {
                    InputAreaMode.INPUT -> {
                        // 输入模式：可切换的模型选择器
                        ChatInputModelSelector(
                            currentModel = selectedModel,
                            onModelChange = onModelChange,
                            enabled = enabled && !isGenerating
                        )
                    }
                    InputAreaMode.DISPLAY -> {
                        // 显示模式：只显示模型名称
                        Text(
                            text = "🤖",
                            style = JewelTheme.defaultTextStyle.copy(fontSize = 8.sp)
                        )
                        Text(
                            text = selectedModel.displayName,
                            style = JewelTheme.defaultTextStyle.copy(
                                fontSize = 9.sp,
                                color = JewelTheme.globalColors.text.normal.copy(alpha = 0.7f)
                            )
                        )
                    }
                }
            }
            
            // 右侧：操作按钮或时间戳
            when (mode) {
                InputAreaMode.INPUT -> {
                    // 输入模式：发送按钮组合
                    SendStopButtonGroup(
                        isGenerating = isGenerating,
                        onSend = onSend,
                        onStop = onStop ?: {},
                        onImageSelected = { imageFile ->
                            val imageRef = ContextReference.ImageReference(
                                path = imageFile.absolutePath,
                                filename = imageFile.name,
                                size = imageFile.length(),
                                mimeType = when (imageFile.extension.lowercase()) {
                                    "jpg", "jpeg" -> "image/jpeg"
                                    "png" -> "image/png"
                                    "gif" -> "image/gif"
                                    "bmp" -> "image/bmp"
                                    "webp" -> "image/webp"
                                    else -> "image/*"
                                }
                            )
                            onImageSelected(imageFile)
                            onContextAdd(imageRef)
                        },
                        hasInput = value.text.isNotBlank(),
                        enabled = enabled
                    )
                }
                InputAreaMode.DISPLAY -> {
                    // 显示模式：时间戳
                    message?.let { msg ->
                        Text(
                            text = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                                .format(Date(msg.timestamp)),
                            style = JewelTheme.defaultTextStyle.copy(
                                fontSize = 9.sp,
                                color = JewelTheme.globalColors.text.normal.copy(alpha = 0.6f)
                            )
                        )
                    }
                }
            }
        }

        // 上下文选择器弹出框 - 现在由UnifiedInputArea管理
        if (showContextSelector && mode == InputAreaMode.INPUT) {
            val contextSearchService = remember(fileIndexService, projectService) {
                ChatInputContextSearchService(fileIndexService, projectService)
            }

            ChatInputContextSelectorPopup(
                onDismiss = {
                    showContextSelector = false
                    atSymbolPosition = null
                },
                onContextSelect = { context ->
                    // 使用新的 generateInlineReference 函数生成标准格式
                    val contextText = generateInlineReference(context)
                    
                    // 对于文件引用，仍然需要添加到内联引用管理器
                    if (context is ContextReference.FileReference) {
                        val inlineRef = InlineFileReference(
                            displayName = context.path.substringAfterLast('/'),
                            fullPath = context.fullPath,
                            relativePath = context.path
                        )
                        inlineReferenceManager.addReference(inlineRef)
                    }

                    if (atSymbolPosition != null) {
                        // @符号触发：安全地替换@
                        val pos = atSymbolPosition!!
                        val currentText = value.text
                        if (pos < currentText.length && currentText[pos] == '@') {
                            val newText = currentText.replaceRange(pos, pos + 1, contextText)
                            val newCursor = pos + contextText.length
                            onValueChange(TextFieldValue(newText, TextRange(newCursor)))
                        }
                    } else {
                        // 按钮或快捷键触发：插入文本
                        val currentText = value.text
                        val cursorPosition = value.selection.start
                        val newText = currentText.substring(0, cursorPosition) + contextText + currentText.substring(cursorPosition)
                        val newCursor = cursorPosition + contextText.length
                        onValueChange(TextFieldValue(newText, TextRange(newCursor)))
                    }

                    showContextSelector = false
                    atSymbolPosition = null
                },
                searchService = contextSearchService
            )
        }
    }
}

/**
 * 只读上下文标签组件
 */
@Composable
private fun ReadOnlyContextTag(
    context: ContextReference,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(
                JewelTheme.globalColors.panelBackground,
                RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = getDisplayTextForReadOnly(context),
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 11.sp,
                color = JewelTheme.globalColors.text.normal
            )
        )
    }
}

/**
 * 获取只读标签的显示文本
 */
private fun getDisplayTextForReadOnly(context: ContextReference): String {
    return when (context) {
        is ContextReference.FileReference -> "@${context.path.substringAfterLast('/')}"
        is ContextReference.WebReference -> {
            context.title?.let { "@$it" } ?: "@${context.url.substringAfterLast('/')}"
        }
        is ContextReference.FolderReference -> "@${context.path.substringAfterLast('/')}"
        is ContextReference.SymbolReference -> "@${context.name}"
        is ContextReference.TerminalReference -> "@terminal"
        is ContextReference.ProblemsReference -> "@problems"
        is ContextReference.GitReference -> "@git"
        is ContextReference.ImageReference -> "@${context.filename}"
        ContextReference.SelectionReference -> "@selection"
        ContextReference.WorkspaceReference -> "@workspace"
    }
}

/**
 * 格式化内联引用显示
 */
private fun formatInlineReferences(text: String): String {
    val pattern = "@([^\\s@]+)".toRegex()
    return pattern.replace(text) { matchResult ->
        val fullPath = matchResult.groupValues[1]
        // 如果是完整路径，提取文件名；否则保持原样
        if (fullPath.contains('/')) {
            "@${fullPath.substringAfterLast('/')}"
        } else {
            matchResult.value
        }
    }
}

/**
 * 上下文搜索服务实现
 */
class ChatInputContextSearchService(
    private val fileIndexService: FileIndexService? = null,
    private val projectService: ProjectService? = null
) : ContextSearchService {
    
    override suspend fun searchFiles(query: String, maxResults: Int): List<FileSearchResult> {
        return try {
            val files = fileIndexService?.searchFiles(query, maxResults) ?: emptyList()
            files.map { fileInfo ->
                val contextItem = FileContextItem(
                    name = fileInfo.name,
                    relativePath = fileInfo.relativePath,
                    absolutePath = fileInfo.absolutePath,
                    isDirectory = fileInfo.isDirectory,
                    fileType = fileInfo.fileType
                )
                
                val weight = when {
                    fileInfo.name.equals(query, ignoreCase = true) -> 100
                    fileInfo.name.startsWith(query, ignoreCase = true) -> 80
                    fileInfo.name.contains(query, ignoreCase = true) -> 60
                    fileInfo.relativePath.contains(query, ignoreCase = true) -> 40
                    else -> 20
                }
                
                val matchType = when {
                    fileInfo.name.equals(query, ignoreCase = true) -> FileSearchResult.MatchType.EXACT_NAME
                    fileInfo.name.startsWith(query, ignoreCase = true) -> FileSearchResult.MatchType.PREFIX_NAME
                    fileInfo.name.contains(query, ignoreCase = true) -> FileSearchResult.MatchType.CONTAINS_NAME
                    else -> FileSearchResult.MatchType.PATH_MATCH
                }
                
                FileSearchResult(contextItem, weight, matchType)
            }.sortedByDescending { it.weight }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    override fun searchFilesFlow(query: String, maxResults: Int) = kotlinx.coroutines.flow.flow {
        emit(searchFiles(query, maxResults))
    }
    
    override suspend fun getRootFiles(maxResults: Int): List<FileContextItem> {
        return try {
            val files = fileIndexService?.getRecentFiles(maxResults) ?: emptyList()
            files.map { fileInfo ->
                FileContextItem(
                    name = fileInfo.name,
                    relativePath = fileInfo.relativePath,
                    absolutePath = fileInfo.absolutePath,
                    isDirectory = fileInfo.isDirectory,
                    fileType = fileInfo.fileType
                )
            }.take(maxResults)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    override fun validateUrl(url: String): Boolean {
        return try {
            val urlPattern = Regex("^(https?|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]")
            url.matches(urlPattern)
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun getWebInfo(url: String): WebContextItem? {
        return if (validateUrl(url)) {
            WebContextItem(
                url = url,
                title = null,
                description = null
            )
        } else {
            null
        }
    }
    
    override suspend fun getFileInfo(relativePath: String): FileContextItem? {
        return try {
            val content = fileIndexService?.getFileContent(relativePath)
            if (content != null) {
                val fileName = relativePath.substringAfterLast('/')
                val absolutePath = projectService?.getProjectPath()?.let { projectPath -> 
                    "$projectPath/$relativePath" 
                } ?: relativePath
                
                FileContextItem(
                    name = fileName,
                    relativePath = relativePath,
                    absolutePath = absolutePath,
                    isDirectory = false,
                    fileType = fileName.substringAfterLast('.', ""),
                    size = content.length.toLong()
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
} 