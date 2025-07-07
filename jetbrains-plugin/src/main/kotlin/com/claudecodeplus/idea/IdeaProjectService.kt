package com.claudecodeplus.idea

import com.claudecodeplus.core.interfaces.ProjectService
import com.intellij.openapi.project.Project
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.options.ShowSettingsUtil

/**
 * IDEA平台的项目服务实现
 */
class IdeaProjectService(
    private val project: Project
) : ProjectService {
    
    override fun getProjectPath(): String {
        return project.basePath ?: System.getProperty("user.dir")
    }
    
    override fun getProjectName(): String {
        return project.name
    }
    
    override fun getRelativePath(absolutePath: String): String {
        val basePath = project.basePath ?: return absolutePath
        return if (absolutePath.startsWith(basePath)) {
            absolutePath.substring(basePath.length + 1)
        } else {
            absolutePath
        }
    }
    
    fun openFile(filePath: String, lineNumber: Int? = null) {
        val virtualFile = VirtualFileManager.getInstance().findFileByUrl("file://$filePath")
        if (virtualFile != null) {
            val descriptor = if (lineNumber != null) {
                OpenFileDescriptor(project, virtualFile, lineNumber - 1, 0)
            } else {
                OpenFileDescriptor(project, virtualFile)
            }
            FileEditorManager.getInstance(project).openTextEditor(descriptor, true)
        }
    }
    
    fun showSettings(settingsId: String? = null) {
        if (settingsId != null) {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, settingsId)
        } else {
            ShowSettingsUtil.getInstance().showSettingsDialog(project)
        }
    }
}