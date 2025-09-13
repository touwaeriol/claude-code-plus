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
        
        // Windows 风格路径 - 冒号处理依赖于操作系统
        val osName = System.getProperty("os.name").lowercase()
        if (osName.contains("windows")) {
            // 在Windows上，冒号会被替换为-，产生双横线
            assertEquals(
                "C--Users-user-project",
                ProjectPathUtils.projectPathToDirectoryName("C:\\Users\\user\\project")
            )
        } else {
            // 在非Windows系统上，normalize可能保留冒号，但点号会被替换为-，导致结果是 C--Users-user-project
            assertEquals(
                "C--Users-user-project",
                ProjectPathUtils.projectPathToDirectoryName("C:\\Users\\user\\project")
            )
        }
        
        // 实际项目路径测试
        assertEquals(
            "-home-testuser-codes-claude-code-plus",
            ProjectPathUtils.projectPathToDirectoryName("/home/testuser/codes/claude-code-plus")
        )
        
        // 测试包含点号的路径
        assertEquals(
            "-Users-testuser--claude-code-router",
            ProjectPathUtils.projectPathToDirectoryName("/Users/testuser/.claude-code-router")
        )
        
        // 测试包含下划线的路径
        assertEquals(
            "-Users-testuser-codes-webstorm-analysis-claude-code",
            ProjectPathUtils.projectPathToDirectoryName("/Users/testuser/codes/webstorm/analysis_claude_code")
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
        
        // Windows 路径在非Windows系统上的处理
        val osName = System.getProperty("os.name").lowercase()
        if (osName.contains("windows")) {
            assertEquals(
                "project",
                ProjectPathUtils.getProjectName("C:\\Users\\user\\project")
            )
        } else {
            // 在非Windows系统上，Windows路径不会被正确解析
            // Paths.get() 会将整个路径作为文件名
            assertEquals(
                "C:\\Users\\user\\project",
                ProjectPathUtils.getProjectName("C:\\Users\\user\\project")
            )
        }
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
        val osName = System.getProperty("os.name").lowercase()
        
        if (osName.contains("windows")) {
            // 在 Windows 上测试 Windows 风格的绝对路径
            assertTrue(ProjectPathUtils.isValidProjectPath("C:\\Users\\project"))
            
            // Unix 风格路径在 Windows 上不被认为是绝对路径
            assertFalse(ProjectPathUtils.isValidProjectPath("/home/user/project"))
        } else {
            // 在 Unix/Mac 上测试 Unix 风格的绝对路径
            assertTrue(ProjectPathUtils.isValidProjectPath("/home/user/project"))
            
            // Windows 路径在非Windows系统上可能不被识别为绝对路径
            assertFalse(ProjectPathUtils.isValidProjectPath("C:\\Users\\project"))
        }
        
        // 相对路径应该无效
        assertFalse(ProjectPathUtils.isValidProjectPath("relative/path"))
        assertFalse(ProjectPathUtils.isValidProjectPath("./project"))
        
        // 空路径应该无效
        assertFalse(ProjectPathUtils.isValidProjectPath(""))
    }
}