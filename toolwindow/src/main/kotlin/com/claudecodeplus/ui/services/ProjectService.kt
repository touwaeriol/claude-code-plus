package com.claudecodeplus.ui.services

/**
 * 项目服务接口
 * 提供项目相关功能的抽象
 */
interface ProjectService {
    /**
     * 获取项目根路径
     */
    fun getProjectPath(): String
    
    /**
     * 获取项目名称
     */
    fun getProjectName(): String
    
    /**
     * 打开文件
     * @param filePath 文件路径
     * @param lineNumber 行号（可选）
     */
    fun openFile(filePath: String, lineNumber: Int? = null)
    
    /**
     * 显示设置对话框
     * @param settingsId 设置页面 ID
     */
    fun showSettings(settingsId: String? = null)
}