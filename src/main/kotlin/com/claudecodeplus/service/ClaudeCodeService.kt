package com.claudecodeplus.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class ClaudeCodeService(private val project: Project) {
    
    fun initialize() {
        // 服务初始化逻辑
    }
    
    fun dispose() {
        // 清理资源
    }
}