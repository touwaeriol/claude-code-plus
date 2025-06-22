package com.claudecodeplus.core.interfaces

/**
 * 项目服务接口，抽象IDEA项目相关操作
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
     * 获取相对路径
     */
    fun getRelativePath(absolutePath: String): String
}