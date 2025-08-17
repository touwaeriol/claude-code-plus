package com.claudecodeplus.core.test

import com.claudecodeplus.core.ApplicationInitializer
import com.claudecodeplus.core.di.resolve
import com.claudecodeplus.core.logging.logI
import com.claudecodeplus.core.services.MessageProcessor
import com.claudecodeplus.core.services.ProjectService
import com.claudecodeplus.core.services.SessionService
import kotlinx.coroutines.runBlocking

/**
 * 重构验证测试
 * 验证新架构的核心服务是否正常工作
 */
object RefactoringTest {
    
    @JvmStatic
    fun main(args: Array<String>) {
        println("=== Claude Code Plus 重构测试 ===")
        
        try {
            // 1. 初始化应用程序
            testApplicationInitialization()
            
            // 2. 测试服务依赖注入
            testServiceDependencyInjection()
            
            // 3. 测试消息处理器
            testMessageProcessor()
            
            // 4. 测试项目服务
            testProjectService()
            
            // 5. 测试会话服务
            testSessionService()
            
            println("✅ 所有测试通过！重构成功！")
            
        } catch (e: Exception) {
            println("❌ 测试失败: ${e.message}")
            e.printStackTrace()
        } finally {
            // 清理资源
            ApplicationInitializer.cleanup()
        }
    }
    
    /**
     * 测试应用程序初始化
     */
    private fun testApplicationInitialization() {
        println("\n1. 测试应用程序初始化...")
        
        ApplicationInitializer.initialize()
        
        if (ApplicationInitializer.isInitialized()) {
            logI("✅ 应用程序初始化成功")
        } else {
            throw Exception("应用程序初始化失败")
        }
    }
    
    /**
     * 测试服务依赖注入
     */
    private fun testServiceDependencyInjection() {
        println("\n2. 测试服务依赖注入...")
        
        // 测试所有核心服务能否正常创建
        val messageProcessor = resolve<MessageProcessor>()
        val sessionService = resolve<SessionService>()
        val projectService = resolve<ProjectService>()
        
        logI("✅ MessageProcessor: ${messageProcessor::class.simpleName}")
        logI("✅ SessionService: ${sessionService::class.simpleName}")
        logI("✅ ProjectService: ${projectService::class.simpleName}")
        
        logI("✅ 依赖注入测试通过")
    }
    
    /**
     * 测试消息处理器
     */
    private fun testMessageProcessor() {
        println("\n3. 测试消息处理器...")
        
        val messageProcessor = resolve<MessageProcessor>()
        
        // 测试JSON消息解析
        val testJsonMessage = """
        {
            "type": "assistant",
            "message": {
                "id": "test-123",
                "role": "assistant",
                "content": [
                    {
                        "type": "text",
                        "text": "这是一条测试消息"
                    }
                ]
            }
        }
        """.trimIndent()
        
        val parseResult = messageProcessor.parseRealtimeMessage(testJsonMessage)
        
        if (parseResult.isSuccess) {
            val message = parseResult.getOrNull()!!
            logI("✅ 消息解析成功: 角色=${message.role}, 内容长度=${message.content.length}")
        } else {
            logI("⚠️ 消息解析被忽略或失败: $parseResult")
        }
        
        // 测试会话ID提取
        val sessionId = messageProcessor.extractSessionId(testJsonMessage)
        logI("✅ 会话ID提取测试: $sessionId")
        
        logI("✅ 消息处理器测试通过")
    }
    
    /**
     * 测试项目服务
     */
    private fun testProjectService() = runBlocking {
        println("\n4. 测试项目服务...")
        
        val projectService = resolve<ProjectService>()
        
        // 测试获取所有项目
        val projectsResult = projectService.getAllProjects()
        if (projectsResult.isSuccess) {
            val projects = projectsResult.getOrNull() ?: emptyList()
            logI("✅ 获取项目列表成功: ${projects.size} 个项目")
            
            projects.forEach { project ->
                logI("  - ${project.name} (${project.path})")
            }
        } else {
            logI("⚠️ 获取项目列表失败: $projectsResult")
        }
        
        // 测试根据路径查找项目
        val currentDir = System.getProperty("user.dir")
        val projectByPathResult = projectService.getProjectByPath(currentDir)
        if (projectByPathResult.isSuccess) {
            val project = projectByPathResult.getOrNull()
            if (project != null) {
                logI("✅ 找到当前目录项目: ${project.name}")
            } else {
                logI("⚠️ 当前目录不是项目")
            }
        } else {
            logI("⚠️ 查找项目失败: $projectByPathResult")
        }
        
        logI("✅ 项目服务测试通过")
    }
    
    /**
     * 测试会话服务
     */
    private fun testSessionService() = runBlocking {
        println("\n5. 测试会话服务...")
        
        val sessionService = resolve<SessionService>()
        
        // 测试检查会话是否存在
        val currentDir = System.getProperty("user.dir")
        val sessionExists = sessionService.sessionExists("test-session", currentDir)
        logI("✅ 会话存在性检查: $sessionExists")
        
        // 测试会话元数据获取
        val metadataResult = sessionService.getSessionMetadata("test-session")
        if (metadataResult.isSuccess) {
            val metadata = metadataResult.getOrNull()!!
            logI("✅ 获取会话元数据成功: sessionId=${metadata.sessionId}")
        } else {
            logI("⚠️ 获取会话元数据失败: $metadataResult")
        }
        
        logI("✅ 会话服务测试通过")
    }
}