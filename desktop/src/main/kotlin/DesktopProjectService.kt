package com.claudecodeplus.desktop

import com.claudecodeplus.ui.services.ProjectService

import java.awt.Desktop
import java.io.File

/**
 * 桌面应用的 ProjectService 实现
 */
class DesktopProjectService(private val projectPath: String) : ProjectService {
    override fun getProjectPath(): String = projectPath
    
    override fun getProjectName(): String = projectPath.substringAfterLast('/')
    
    override fun openFile(filePath: String, lineNumber: Int?) {
        try {
            val file = File(filePath)
            if (file.exists() && Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(file)
                println("正在打开文件: $filePath")
            } else {
                println("错误: 无法打开文件，文件不存在或不支持此操作。")
            }
        } catch (e: Exception) {
            println("打开文件时出错: ${e.message}")
        }
    }
    
    override fun showSettings(settingsId: String?) {
        // 通过 ServiceContainer 获取 UI 状态并显示设置对话框
        com.claudecodeplus.desktop.di.ServiceContainer.appUiState.isSettingsVisible = true
        println("显示设置: ${settingsId ?: "通用设置"}")
    }
}