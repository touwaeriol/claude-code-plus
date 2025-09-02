package com.claudecodeplus.plugin.handlers

import com.claudecodeplus.ui.models.ToolCall
import com.claudecodeplus.ui.models.ToolCallStatus
import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.notification.NotificationGroupManager
import java.io.File
import java.nio.file.Paths

/**
 * Edit 工具点击处理器
 * 使用 IDEA 的 Diff 视图显示文件变更
 */
class EditToolHandler : ToolClickHandler {
    
    companion object {
        private val logger = Logger.getInstance(EditToolHandler::class.java)
        private val NOTIFICATION_GROUP_ID = "Claude Code Plus"
    }
    
    override fun canHandle(toolCall: ToolCall): Boolean {
        return (toolCall.name.equals("Edit", ignoreCase = true) || 
                toolCall.name.equals("MultiEdit", ignoreCase = true)) && 
               toolCall.status == ToolCallStatus.SUCCESS
    }
    
    override fun handleToolClick(
        toolCall: ToolCall,
        project: Project?,
        config: ToolClickConfig
    ): Boolean {
        if (project == null) {
            logger.info("EditToolHandler: Project is null, 无法使用 IDE 集成")
            return false
        }
        
        if (config.alwaysExpand) {
            logger.info("EditToolHandler: 配置为总是展开，跳过 IDE 集成")
            return false
        }
        
        return try {
            val editInfo = parseEditToolCall(toolCall, project)
            if (editInfo != null) {
                showDiffInEditor(project, editInfo, config)
                true
            } else {
                logger.warn("EditToolHandler: 无法解析工具调用参数")
                false
            }
        } catch (e: Exception) {
            logger.error("EditToolHandler: 处理失败", e)
            if (config.fallbackBehavior == FallbackBehavior.SHOW_ERROR) {
                showErrorNotification(project, "显示文件差异失败: ${e.message}")
            }
            config.fallbackBehavior == FallbackBehavior.EXPAND
        }
    }
    
    /**
     * 解析 Edit 工具调用参数
     */
    private fun parseEditToolCall(toolCall: ToolCall, project: Project?): EditFileInfo? {
        return when {
            // 标准 Edit 工具 - 改进为显示完整文件内容
            toolCall.parameters.containsKey("file_path") -> {
                val filePath = toolCall.parameters["file_path"] as? String ?: return null
                val oldString = toolCall.parameters["old_string"] as? String ?: ""
                val newString = toolCall.parameters["new_string"] as? String ?: ""
                val replaceAll = toolCall.parameters["replace_all"] as? Boolean ?: false
                
                // 使用反向编辑恢复原始文件内容
                val originalFileContent = getOriginalFileContent(project, filePath, oldString, newString, replaceAll)
                
                // 读取当前文件内容（已被Claude修改）
                val modifiedFileContent = readFileContent(filePath) ?: ""
                
                EditFileInfo(
                    filePath = filePath,
                    oldContent = originalFileContent,  // 完整原始文件
                    newContent = modifiedFileContent, // 完整修改后文件
                    isMultiEdit = false
                )
            }
            
            // MultiEdit 工具
            toolCall.parameters.containsKey("edits") -> {
                val filePath = toolCall.parameters["file_path"] as? String ?: return null
                val edits = toolCall.parameters["edits"] as? List<*> ?: return null
                
                // 获取当前文件内容（已被Claude修改）
                val modifiedContent = readFileContent(filePath) ?: ""
                // 使用反向编辑恢复原始文件内容
                val originalContent = getOriginalFileContentForMultiEdit(project, filePath, edits)
                
                EditFileInfo(
                    filePath = filePath,
                    oldContent = originalContent,
                    newContent = modifiedContent,
                    isMultiEdit = true
                )
            }
            
            else -> null
        }
    }
    
    /**
     * 读取文件内容
     */
    private fun readFileContent(filePath: String): String? {
        return try {
            val file = File(filePath)
            if (file.exists() && file.isFile) {
                file.readText()
            } else {
                null
            }
        } catch (e: Exception) {
            logger.warn("EditToolHandler: 读取文件内容失败: $filePath", e)
            null
        }
    }
    
    /**
     * 应用多个编辑操作
     */
    private fun applyMultipleEdits(content: String, edits: List<*>): String {
        var result = content
        
        try {
            edits.forEach { edit ->
                if (edit is Map<*, *>) {
                    val oldString = edit["old_string"] as? String
                    val newString = edit["new_string"] as? String
                    val replaceAll = edit["replace_all"] as? Boolean ?: false
                    
                    if (oldString != null && newString != null) {
                        result = if (replaceAll) {
                            result.replace(oldString, newString)
                        } else {
                            result.replaceFirst(oldString, newString)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.warn("EditToolHandler: 应用编辑操作失败", e)
        }
        
        return result
    }
    
    /**
     * 获取原始文件内容（单个Edit操作）
     * 使用反向编辑恢复原始内容
     */
    private fun getOriginalFileContent(
        project: Project?,
        filePath: String,
        oldString: String,
        newString: String,
        replaceAll: Boolean
    ): String {
        // 读取当前文件内容（已被Claude修改）
        val currentContent = readFileContent(filePath) ?: ""
        
        logger.info("EditToolHandler: 使用反向编辑恢复原始内容")
        logger.debug("EditToolHandler: oldString='$oldString', newString='$newString', replaceAll=$replaceAll")
        
        return try {
            // 反向操作：将 newString 替换回 oldString
            val originalContent = if (replaceAll) {
                currentContent.replace(newString, oldString)
            } else {
                currentContent.replaceFirst(newString, oldString)
            }
            
            logger.debug("EditToolHandler: 反向编辑成功")
            originalContent
        } catch (e: Exception) {
            logger.error("EditToolHandler: 反向编辑失败", e)
            currentContent
        }
    }
    
    /**
     * 获取原始文件内容（MultiEdit操作）
     * 使用反向编辑恢复原始内容
     */
    private fun getOriginalFileContentForMultiEdit(
        project: Project?,
        filePath: String,
        edits: List<*>
    ): String {
        // 读取当前文件内容（已被Claude修改）
        val currentContent = readFileContent(filePath) ?: ""
        
        logger.info("EditToolHandler: MultiEdit使用反向编辑恢复原始内容")
        logger.debug("EditToolHandler: 共有 ${edits.size} 个编辑操作")
        
        return try {
            var result = currentContent
            
            // 反向应用编辑操作，从最后一个编辑开始
            // 因为 MultiEdit 是按顺序应用的，所以要反向撤销
            edits.reversed().forEachIndexed { index, edit ->
                if (edit is Map<*, *>) {
                    val oldString = edit["old_string"] as? String
                    val newString = edit["new_string"] as? String
                    val replaceAll = edit["replace_all"] as? Boolean ?: false
                    
                    logger.debug("EditToolHandler: 反向编辑 #${edits.size - index}: oldString='$oldString', newString='$newString'")
                    
                    if (oldString != null && newString != null) {
                        result = if (replaceAll) {
                            result.replace(newString, oldString)
                        } else {
                            result.replaceFirst(newString, oldString)
                        }
                    }
                }
            }
            
            logger.debug("EditToolHandler: MultiEdit反向编辑成功")
            result
        } catch (e: Exception) {
            logger.error("EditToolHandler: MultiEdit反向编辑失败", e)
            currentContent
        }
    }
    
    
    /**
     * 在编辑器中显示 Diff
     */
    private fun showDiffInEditor(
        project: Project,
        editInfo: EditFileInfo,
        config: ToolClickConfig
    ) {
        ApplicationManager.getApplication().invokeLater {
            try {
                val diffContentFactory = DiffContentFactory.getInstance()
                
                // 创建 DiffContent
                val oldContent = diffContentFactory.create(editInfo.oldContent)
                val newContent = diffContentFactory.create(editInfo.newContent)
                
                // 设置内容标题 - 改进为更清晰的文件对比标题
                val fileName = File(editInfo.filePath).name
                val oldTitle = "$fileName (原始)"
                val newTitle = if (editInfo.isMultiEdit) "$fileName (多处修改后)" else "$fileName (修改后)"
                
                // 创建 SimpleDiffRequest
                val diffRequest = SimpleDiffRequest(
                    "文件变更: $fileName",
                    oldContent,
                    newContent,
                    oldTitle,
                    newTitle
                )
                
                // 显示 Diff 对话框
                DiffManager.getInstance().showDiff(project, diffRequest)
                
                if (config.showNotifications) {
                    val message = if (editInfo.isMultiEdit) {
                        "已显示文件的多处变更差异"
                    } else {
                        "已显示文件变更差异"
                    }
                    showInfoNotification(project, message)
                }
                
            } catch (e: Exception) {
                logger.error("EditToolHandler: 显示差异失败", e)
                if (config.showNotifications) {
                    showErrorNotification(project, "显示文件差异失败: ${e.message}")
                }
            }
        }
    }
    
    /**
     * 查找虚拟文件（与 ReadToolHandler 类似的实现）
     */
    private fun findVirtualFile(project: Project, filePath: String): VirtualFile? {
        return try {
            val file = File(filePath)
            if (file.isAbsolute && file.exists()) {
                LocalFileSystem.getInstance().findFileByPath(file.canonicalPath)
            } else {
                val projectBasePath = project.basePath
                if (projectBasePath != null) {
                    val absolutePath = Paths.get(projectBasePath, filePath).toString()
                    val absoluteFile = File(absolutePath)
                    if (absoluteFile.exists()) {
                        LocalFileSystem.getInstance().findFileByPath(absoluteFile.canonicalPath)
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            logger.warn("EditToolHandler: 查找文件失败: $filePath", e)
            null
        }
    }
    
    /**
     * 显示通知
     */
    private fun showInfoNotification(project: Project, message: String) {
        showNotification(project, message, NotificationType.INFORMATION)
    }
    
    private fun showWarningNotification(project: Project, message: String) {
        showNotification(project, message, NotificationType.WARNING)
    }
    
    private fun showErrorNotification(project: Project, message: String) {
        showNotification(project, message, NotificationType.ERROR)
    }
    
    private fun showNotification(project: Project, message: String, type: NotificationType) {
        try {
            val notificationGroup = NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP_ID)
            
            val notification = notificationGroup.createNotification(
                "Claude Code Plus",
                message,
                type
            )
            
            Notifications.Bus.notify(notification, project)
        } catch (e: Exception) {
            logger.warn("EditToolHandler: 显示通知失败", e)
        }
    }
}

/**
 * Edit 文件信息数据类
 */
private data class EditFileInfo(
    val filePath: String,
    val oldContent: String,
    val newContent: String,
    val isMultiEdit: Boolean = false
)