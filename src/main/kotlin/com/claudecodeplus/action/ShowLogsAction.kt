package com.claudecodeplus.action

import com.claudecodeplus.util.ResponseLogger
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.ui.Messages
import java.io.File

/**
 * 显示日志文件的动作
 */
class ShowLogsAction : AnAction("Show Claude Code Logs") {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        val recentLogs = ResponseLogger.getRecentLogs(10, project)
        
        if (recentLogs.isEmpty()) {
            Messages.showInfoMessage(
                project,
                "没有找到日志文件。\n日志将在使用插件时自动创建。",
                "Claude Code Plus 日志"
            )
            return
        }
        
        // 创建选择列表
        val logNames = recentLogs.map { file ->
            "${file.name} (${formatFileSize(file.length())})"
        }.toTypedArray()
        
        val selectedIndex = Messages.showChooseDialog(
            project,
            "选择要查看的日志文件：",
            "Claude Code Plus 日志",
            null,
            logNames,
            logNames.firstOrNull()
        )
        
        if (selectedIndex >= 0) {
            val selectedFile = recentLogs[selectedIndex]
            openFileInEditor(project, selectedFile)
        }
    }
    
    private fun openFileInEditor(project: com.intellij.openapi.project.Project, file: File) {
        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)
        if (virtualFile != null) {
            FileEditorManager.getInstance(project).openFile(virtualFile, true)
        } else {
            Messages.showErrorDialog(
                project,
                "无法打开文件: ${file.absolutePath}",
                "错误"
            )
        }
    }
    
    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            else -> "${size / (1024 * 1024)} MB"
        }
    }
}