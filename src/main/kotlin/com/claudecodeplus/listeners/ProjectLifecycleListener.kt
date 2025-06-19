package com.claudecodeplus.listeners

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.diagnostic.Logger

/**
 * 项目生命周期监听器
 * 管理 Node 服务的生命周期
 */
class ProjectLifecycleListener : ProjectManagerListener {
    
    companion object {
        private val logger = Logger.getInstance(ProjectLifecycleListener::class.java)
        private val projectServices = mutableMapOf<Project, Any>()
    }
    
    override fun projectOpened(project: Project) {
        logger.info("Project opened: ${project.name}")
        // API 客户端会在工具窗口创建时初始化
    }
    
    override fun projectClosing(project: Project) {
        logger.info("Project closing: ${project.name}")
        // 停止该项目相关的服务
        projectServices.remove(project)?.let {
            // 如果有项目特定的服务，在这里停止
        }
    }
    
    override fun projectClosed(project: Project) {
        logger.info("Project closed: ${project.name}")
    }
    
    /**
     * 注册项目服务
     */
    fun registerProjectService(project: Project, service: Any) {
        projectServices[project] = service
    }
}