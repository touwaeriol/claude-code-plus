/*
 * ChatInputArea.kt
 * 
 * 完整的聊天输入区域组件 - 使用独立的子组件组合而成
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
import androidx.compose.ui.unit.dp
import com.claudecodeplus.ui.models.*
import com.claudecodeplus.ui.jewel.components.context.*
import com.claudecodeplus.ui.services.FileSearchService
import com.claudecodeplus.ui.services.ProjectService
import org.jetbrains.jewel.foundation.theme.JewelTheme

/**
 * 完整的聊天输入区域组件
 * 
 * @param text 当前输入文本
 * @param onTextChange 文本变化回调
 * @param onSend 发送消息回调
 * @param onStop 停止生成回调
 * @param contexts 当前上下文列表
 * @param onContextAdd 添加上下文回调
 * @param onContextRemove 移除上下文回调
 * @param isGenerating 是否正在生成
 * @param enabled 是否启用输入
 * @param selectedModel 当前选择的模型
 * @param onModelChange 模型变化回调
 * @param fileIndexService 文件索引服务
 * @param projectService 项目服务
 * @param inlineReferenceManager 内联引用管理器
 */
@Composable
fun ChatInputArea(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: (() -> Unit)? = null,
    contexts: List<ContextReference> = emptyList(),
    onContextAdd: (ContextReference) -> Unit = {},
    onContextRemove: (ContextReference) -> Unit = {},
    isGenerating: Boolean = false,
    enabled: Boolean = true,
    selectedModel: AiModel = AiModel.OPUS,
    onModelChange: (AiModel) -> Unit = {},
    fileIndexService: com.claudecodeplus.ui.services.FileIndexService? = null,
    projectService: com.claudecodeplus.ui.services.ProjectService? = null,
    inlineReferenceManager: InlineReferenceManager = remember { InlineReferenceManager() },
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    
    // 创建上下文搜索服务
    val contextSearchService = remember(fileIndexService, projectService) {
        ChatInputContextSearchService(fileIndexService, projectService)
    }
    
    // 整个输入区域使用统一边框
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
        // 第一行：Add Context 按钮和上下文标签
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 使用独立的 Add Context 按钮组件
            AddContextButton(
                onClick = { 
                    // 触发添加上下文，这里简化为添加一个空的文件引用，
                    // 实际应该触发上下文选择器
                    onContextAdd(ContextReference.FileReference("")) 
                },
                enabled = enabled && !isGenerating
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // 上下文标签显示在同一行
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(contexts) { context ->
                    ContextTag(
                        context = context,
                        onRemove = { onContextRemove(context) }
                    )
                }
            }
        }
        
        // 第二行：使用独立的输入框组件
        ChatInputField(
            text = text,
            onTextChange = onTextChange,
            onSend = onSend,
            onContextAdd = onContextAdd,
            enabled = enabled && !isGenerating,
            searchService = contextSearchService,
            inlineReferenceManager = inlineReferenceManager,
            focusRequester = focusRequester
        )
        
        // 第三行：底部工具栏（模型选择器和发送按钮）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 使用独立的模型选择器组件
            ChatInputModelSelector(
                currentModel = selectedModel,
                onModelChange = onModelChange,
                enabled = enabled && !isGenerating
            )
            
            // 使用独立的发送/停止按钮组件和图片选择按钮
            SendStopButtonGroup(
                isGenerating = isGenerating,
                onSend = onSend,
                onStop = onStop ?: {},
                onImageSelected = { imageFile ->
                    // 创建图片上下文引用
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
                    onContextAdd(imageRef)
                },
                hasInput = text.isNotBlank(),
                enabled = enabled
            )
        }
    }
}



/**
 * 上下文搜索服务实现
 */
class ChatInputContextSearchService(
    private val fileIndexService: com.claudecodeplus.ui.services.FileIndexService? = null,
    private val projectService: com.claudecodeplus.ui.services.ProjectService? = null
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