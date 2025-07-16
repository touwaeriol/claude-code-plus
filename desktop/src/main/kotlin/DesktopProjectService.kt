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
        // TODO: 实现显示设置功能 - 可以弹出设置对话框
        println("显示设置: ${settingsId ?: "通用设置"}")
    }
}