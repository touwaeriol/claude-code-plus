package com.claudecodeplus.sdk

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ProjectPathUtilsTest {
    
    @Test
    fun testProjectPathToDirectoryName() {
        // Unix 风格路径 - 保留开头的 -
        assertEquals(
            "-home-user-project",
            ProjectPathUtils.projectPathToDirectoryName("/home/user/project")
        )
        
        // Windows 风格路径 - C: 被转换为 C
        assertEquals(
            "C-Users-user-project",
            ProjectPathUtils.projectPathToDirectoryName("C:\\Users\\user\\project")
        )
        
        // 实际项目路径测试
        assertEquals(
            "-home-erio-codes-claude-code-plus",
            ProjectPathUtils.projectPathToDirectoryName("/home/erio/codes/claude-code-plus")
        )
        
        // 混合路径
        assertEquals(
            "-home-user-my-project",
            ProjectPathUtils.projectPathToDirectoryName("/home/user/my-project")
        )
        
        // 带尾部斜杠 - 应该被规范化处理
        assertEquals(
            "-home-user-project",
            ProjectPathUtils.projectPathToDirectoryName("/home/user/project/")
        )
    }
    
    @Test
    fun testGetProjectName() {
        assertEquals(
            "project",
            ProjectPathUtils.getProjectName("/home/user/project")
        )
        
        assertEquals(
            "claude-code-plus",
            ProjectPathUtils.getProjectName("/home/erio/codes/claude-code-plus")
        )
        
        assertEquals(
            "project",
            ProjectPathUtils.getProjectName("C:\\Users\\user\\project")
        )
    }
    
    @Test
    fun testGenerateProjectId() {
        val id1 = ProjectPathUtils.generateProjectId("/home/user/project1")
        val id2 = ProjectPathUtils.generateProjectId("/home/user/project2")
        val id3 = ProjectPathUtils.generateProjectId("/home/user/project1")
        
        // 相同路径应该生成相同的 ID
        assertEquals(id1, id3)
        
        // 不同路径应该生成不同的 ID
        assertNotEquals(id1, id2)
        
        // ID 长度应该是 8
        assertEquals(8, id1.length)
    }
    
    @Test
    fun testIsValidProjectPath() {
        // 绝对路径应该有效
        assertTrue(ProjectPathUtils.isValidProjectPath("/home/user/project"))
        assertTrue(ProjectPathUtils.isValidProjectPath("C:\\Users\\project"))
        
        // 相对路径应该无效
        assertFalse(ProjectPathUtils.isValidProjectPath("relative/path"))
        assertFalse(ProjectPathUtils.isValidProjectPath("./project"))
        
        // 空路径应该无效
        assertFalse(ProjectPathUtils.isValidProjectPath(""))
    }
    
    @Test
    fun testDirectoryNameToProjectPaths() {
        // Unix 风格路径（带开头的 -）
        val paths = ProjectPathUtils.directoryNameToProjectPaths("-home-user-project")
        assertTrue(paths.contains("/home/user/project"))
        
        // Windows 风格路径测试
        val winPaths = ProjectPathUtils.directoryNameToProjectPaths("C-Users-project")
        assertTrue(winPaths.any { it.startsWith("C:") })
        
        // 实际的项目目录名测试
        val actualPaths = ProjectPathUtils.directoryNameToProjectPaths("-home-erio-codes-claude-code-plus")
        assertTrue(actualPaths.contains("/home/erio/codes/claude/code/plus"))
    }
}