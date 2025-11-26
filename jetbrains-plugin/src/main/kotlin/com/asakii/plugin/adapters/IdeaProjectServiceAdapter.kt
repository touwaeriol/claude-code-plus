package com.asakii.plugin.adapters

import com.asakii.core.interfaces.ProjectService
import com.intellij.openapi.project.Project
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.diagnostic.Logger

/**
 * IntelliJ IDEA 的 ProjectService 实现
 */
class IdeaProjectServiceAdapter(
    private val project: Project
) : ProjectService {
    
    companion object {
        private val logger = Logger.getInstance(IdeaProjectServiceAdapter::class.java)
    }
    
    override fun getProjectPath(): String {
        return project.basePath ?: ""
    }
    
    override fun getProjectName(): String {
        return project.name
    }
    
    override fun getRelativePath(absolutePath: String): String {
        val projectPath = getProjectPath()
        return if (absolutePath.startsWith(projectPath)) {
            absolutePath.removePrefix(projectPath).removePrefix("/").removePrefix("\\")
        } else {
            absolutePath
        }
    }
    
    override fun openFile(filePath: String, lineNumber: Int?) {
        try {
            val virtualFile = LocalFileSystem.getInstance().findFileByPath(filePath)
            if (virtualFile != null) {
                val descriptor = if (lineNumber != null && lineNumber > 0) {
                    OpenFileDescriptor(project, virtualFile, lineNumber - 1, 0)
                } else {
                    OpenFileDescriptor(project, virtualFile)
                }
                FileEditorManager.getInstance(project).openTextEditor(descriptor, true)
            } else {
                logger.warn("File not found: $filePath")
            }
        } catch (e: Exception) {
            logger.error("Error opening file: $filePath", e)
        }
    }
    
    override fun showSettings(settingsId: String?) {
        if (settingsId != null) {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, settingsId)
        } else {
            ShowSettingsUtil.getInstance().showSettingsDialog(project)
        }
    }
}