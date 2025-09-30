package com.claudecodeplus.sdk

import com.claudecodeplus.sdk.types.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import kotlinx.coroutines.delay

/**
 * 测试 Claude Code SDK 中的自定义工具和 hooks 功能集成。
 * 验证自定义工具能否正常工作，以及 hooks 能否正确拦截和处理这些工具。
 */
class CustomToolsHooksTest {
    
    private lateinit var toolExecutionLog: MutableList<String>
    private lateinit var hookExecutionLog: MutableList<String>
    
    @BeforeEach
    fun setUp() {
        toolExecutionLog = mutableListOf()
        hookExecutionLog = mutableListOf()
    }
    
    @Test
    fun `test custom tool detection and validation hooks`() = runBlocking {
        // 测试自定义工具检测和验证 hooks
        val customToolValidator: HookCallback = { input, toolUseId, context ->
            val toolName = input["tool_name"] as? String ?: "unknown"
            hookExecutionLog.add("Validating tool: $toolName")
            
            when (toolName) {
                // 验证标准工具
                "Read", "Write", "Edit", "Bash" -> {
                    HookJSONOutput(
                        systemMessage = "✅ Standard tool $toolName validated"
                    )
                }
                // 验证自定义工具
                "DatabaseQuery", "APICall", "DockerCommand" -> {
                    val toolInput = input["tool_input"] as? Map<*, *> ?: emptyMap<String, Any>()
                    
                    // 对自定义工具进行特殊验证
                    when (toolName) {
                        "DatabaseQuery" -> {
                            val query = toolInput["query"] as? String ?: ""
                            if (query.uppercase().contains("DROP") || query.uppercase().contains("DELETE")) {
                                HookJSONOutput(
                                    decision = "block",
                                    systemMessage = "🚫 Dangerous database operation blocked: $query",
                                    hookSpecificOutput = JsonPrimitive("dangerous_db_operation")
                                )
                            } else {
                                HookJSONOutput(
                                    systemMessage = "✅ Database query validated: $query"
                                )
                            }
                        }
                        "APICall" -> {
                            val endpoint = toolInput["endpoint"] as? String ?: ""
                            val method = toolInput["method"] as? String ?: "GET"
                            
                            if (endpoint.contains("admin") && method == "DELETE") {
                                HookJSONOutput(
                                    decision = "block",
                                    systemMessage = "🚫 Admin deletion blocked: $endpoint",
                                    hookSpecificOutput = JsonPrimitive("admin_protection")
                                )
                            } else {
                                HookJSONOutput(
                                    systemMessage = "✅ API call validated: $method $endpoint"
                                )
                            }
                        }
                        "DockerCommand" -> {
                            val command = toolInput["command"] as? String ?: ""
                            val dangerousCommands = listOf("rm", "rmi", "system prune")
                            
                            if (dangerousCommands.any { command.contains(it) }) {
                                HookJSONOutput(
                                    decision = "block",
                                    systemMessage = "🚫 Dangerous Docker command blocked: $command",
                                    hookSpecificOutput = JsonPrimitive("dangerous_docker_op")
                                )
                            } else {
                                HookJSONOutput(
                                    systemMessage = "✅ Docker command validated: $command"
                                )
                            }
                        }
                        else -> HookJSONOutput(systemMessage = "✅ Custom tool $toolName validated")
                    }
                }
                else -> {
                    HookJSONOutput(
                        decision = "block",
                        systemMessage = "🚫 Unknown tool $toolName not allowed",
                        hookSpecificOutput = JsonPrimitive("unknown_tool")
                    )
                }
            }
        }
        
        // 测试场景：安全的数据库查询
        val safeDatabaseQuery = mapOf(
            "tool_name" to "DatabaseQuery",
            "tool_input" to mapOf(
                "query" to "SELECT * FROM users WHERE active = 1",
                "database" to "main"
            )
        )
        
        val result1 = customToolValidator(safeDatabaseQuery, "db_query_1", HookContext())
        assertNull(result1.decision) // 应该被允许
        assertTrue(result1.systemMessage!!.contains("✅"))
        assertTrue(result1.systemMessage!!.contains("Database query validated"))
        
        // 测试场景：危险的数据库操作
        val dangerousDatabaseQuery = mapOf(
            "tool_name" to "DatabaseQuery",
            "tool_input" to mapOf(
                "query" to "DROP TABLE users",
                "database" to "main"
            )
        )
        
        val result2 = customToolValidator(dangerousDatabaseQuery, "db_query_2", HookContext())
        assertEquals("block", result2.decision) // 应该被阻止
        assertTrue(result2.systemMessage!!.contains("🚫"))
        assertTrue(result2.systemMessage!!.contains("Dangerous database operation blocked"))
        assertEquals(JsonPrimitive("dangerous_db_operation"), result2.hookSpecificOutput)
        
        // 测试场景：安全的API调用
        val safeAPICall = mapOf(
            "tool_name" to "APICall",
            "tool_input" to mapOf(
                "endpoint" to "https://api.example.com/users",
                "method" to "GET",
                "headers" to mapOf("Authorization" to "Bearer token")
            )
        )
        
        val result3 = customToolValidator(safeAPICall, "api_call_1", HookContext())
        assertNull(result3.decision)
        assertTrue(result3.systemMessage!!.contains("✅"))
        assertTrue(result3.systemMessage!!.contains("API call validated"))
        
        // 测试场景：危险的API调用
        val dangerousAPICall = mapOf(
            "tool_name" to "APICall",
            "tool_input" to mapOf(
                "endpoint" to "https://api.example.com/admin/users/123",
                "method" to "DELETE"
            )
        )
        
        val result4 = customToolValidator(dangerousAPICall, "api_call_2", HookContext())
        assertEquals("block", result4.decision)
        assertTrue(result4.systemMessage!!.contains("🚫"))
        assertTrue(result4.systemMessage!!.contains("Admin deletion blocked"))
        assertEquals(JsonPrimitive("admin_protection"), result4.hookSpecificOutput)
        
        // 测试场景：安全的Docker命令
        val safeDockerCommand = mapOf(
            "tool_name" to "DockerCommand",
            "tool_input" to mapOf(
                "command" to "docker ps -a",
                "container" to "myapp"
            )
        )
        
        val result5 = customToolValidator(safeDockerCommand, "docker_cmd_1", HookContext())
        assertNull(result5.decision)
        assertTrue(result5.systemMessage!!.contains("✅"))
        assertTrue(result5.systemMessage!!.contains("Docker command validated"))
        
        // 测试场景：危险的Docker命令
        val dangerousDockerCommand = mapOf(
            "tool_name" to "DockerCommand",
            "tool_input" to mapOf(
                "command" to "docker system prune -af",
                "force" to true
            )
        )
        
        val result6 = customToolValidator(dangerousDockerCommand, "docker_cmd_2", HookContext())
        assertEquals("block", result6.decision)
        assertTrue(result6.systemMessage!!.contains("🚫"))
        assertTrue(result6.systemMessage!!.contains("Dangerous Docker command blocked"))
        assertEquals(JsonPrimitive("dangerous_docker_op"), result6.hookSpecificOutput)
        
        // 验证hook执行日志
        assertEquals(6, hookExecutionLog.size)
        assertTrue(hookExecutionLog.all { it.startsWith("Validating tool:") })
        
        println("✅ Custom tool validation hooks test passed")
    }
    
    @Test
    fun `test custom tool execution logging and monitoring`() = runBlocking {
        // 测试自定义工具执行日志记录和监控
        val toolExecutionTracker: HookCallback = { input, toolUseId, context ->
            val toolName = input["tool_name"] as? String ?: "unknown"
            val toolInput = input["tool_input"] as? Map<*, *> ?: emptyMap<String, Any>()
            
            hookExecutionLog.add("Executed: $toolName with ID: $toolUseId")
            
            // 记录自定义工具的详细执行信息
            when (toolName) {
                "DatabaseQuery" -> {
                    val query = toolInput["query"] as? String ?: ""
                    toolExecutionLog.add("DB Query: $query")
                }
                "APICall" -> {
                    val endpoint = toolInput["endpoint"] as? String ?: ""
                    val method = toolInput["method"] as? String ?: "GET"
                    toolExecutionLog.add("API Call: $method $endpoint")
                }
                "DockerCommand" -> {
                    val command = toolInput["command"] as? String ?: ""
                    toolExecutionLog.add("Docker: $command")
                }
                "FileSystem" -> {
                    val operation = toolInput["operation"] as? String ?: ""
                    val path = toolInput["path"] as? String ?: ""
                    toolExecutionLog.add("FileSystem: $operation on $path")
                }
            }
            
            HookJSONOutput(
                systemMessage = "📊 Tool execution tracked: $toolName"
            )
        }
        
        // 模拟多种自定义工具的执行
        val testScenarios = listOf(
            Triple("DatabaseQuery", mapOf("query" to "SELECT COUNT(*) FROM orders"), "db_001"),
            Triple("APICall", mapOf("endpoint" to "/api/users", "method" to "POST"), "api_001"),
            Triple("DockerCommand", mapOf("command" to "docker logs myapp"), "docker_001"),
            Triple("FileSystem", mapOf("operation" to "read", "path" to "/tmp/data.txt"), "fs_001")
        )
        
        testScenarios.forEach { (toolName, toolInput, toolId) ->
            val input = mapOf(
                "tool_name" to toolName,
                "tool_input" to toolInput
            )
            
            val result = toolExecutionTracker(input, toolId, HookContext())
            assertEquals("📊 Tool execution tracked: $toolName", result.systemMessage)
        }
        
        // 验证执行日志
        assertEquals(4, hookExecutionLog.size)
        assertEquals(4, toolExecutionLog.size)
        
        assertTrue(hookExecutionLog.contains("Executed: DatabaseQuery with ID: db_001"))
        assertTrue(hookExecutionLog.contains("Executed: APICall with ID: api_001"))
        assertTrue(hookExecutionLog.contains("Executed: DockerCommand with ID: docker_001"))
        assertTrue(hookExecutionLog.contains("Executed: FileSystem with ID: fs_001"))
        
        assertTrue(toolExecutionLog.contains("DB Query: SELECT COUNT(*) FROM orders"))
        assertTrue(toolExecutionLog.contains("API Call: POST /api/users"))
        assertTrue(toolExecutionLog.contains("Docker: docker logs myapp"))
        assertTrue(toolExecutionLog.contains("FileSystem: read on /tmp/data.txt"))
        
        println("✅ Custom tool execution tracking test passed")
    }
    
    @Test
    fun `test comprehensive custom tool hook configuration`() {
        // 测试完整的自定义工具 hook 配置
        val securityHook: HookCallback = { input, toolUseId, context ->
            val toolName = input["tool_name"] as? String ?: ""
            HookJSONOutput(systemMessage = "🔒 Security check for $toolName")
        }
        
        val auditHook: HookCallback = { input, toolUseId, context ->
            val toolName = input["tool_name"] as? String ?: ""
            HookJSONOutput(systemMessage = "📋 Audit log for $toolName")
        }
        
        val performanceHook: HookCallback = { input, toolUseId, context ->
            val toolName = input["tool_name"] as? String ?: ""
            HookJSONOutput(systemMessage = "⚡ Performance monitoring for $toolName")
        }
        
        // 配置所有6种 hook 事件类型，针对自定义工具
        val customToolsHooksConfig = mapOf(
            HookEvent.PRE_TOOL_USE to listOf(
                HookMatcher(
                    matcher = "DatabaseQuery|APICall|DockerCommand",
                    hooks = listOf(securityHook)
                ),
                HookMatcher(
                    matcher = "FileSystem|NetworkTool",
                    hooks = listOf(securityHook, auditHook)
                )
            ),
            HookEvent.POST_TOOL_USE to listOf(
                HookMatcher(
                    matcher = null, // 所有工具
                    hooks = listOf(auditHook, performanceHook)
                )
            ),
            HookEvent.USER_PROMPT_SUBMIT to listOf(
                HookMatcher(
                    matcher = null,
                    hooks = listOf(auditHook)
                )
            ),
            HookEvent.STOP to listOf(
                HookMatcher(
                    matcher = null,
                    hooks = listOf(auditHook)
                )
            ),
            HookEvent.SUBAGENT_STOP to listOf(
                HookMatcher(
                    matcher = null,
                    hooks = listOf(auditHook)
                )
            ),
            HookEvent.PRE_COMPACT to listOf(
                HookMatcher(
                    matcher = null,
                    hooks = listOf(performanceHook)
                )
            )
        )
        
        val options = ClaudeCodeOptions(
            hooks = customToolsHooksConfig,
            allowedTools = listOf(
                // 标准工具
                "Read", "Write", "Edit", "Bash",
                // 自定义工具
                "DatabaseQuery", "APICall", "DockerCommand", "FileSystem", "NetworkTool"
            )
        )
        
        // 验证配置
        assertNotNull(options.hooks)
        assertEquals(6, options.hooks!!.size)
        
        // 验证 PRE_TOOL_USE 配置
        val preToolUseMatchers = options.hooks!![HookEvent.PRE_TOOL_USE]!!
        assertEquals(2, preToolUseMatchers.size)
        assertEquals("DatabaseQuery|APICall|DockerCommand", preToolUseMatchers[0].matcher)
        assertEquals("FileSystem|NetworkTool", preToolUseMatchers[1].matcher)
        assertEquals(1, preToolUseMatchers[0].hooks.size)
        assertEquals(2, preToolUseMatchers[1].hooks.size)
        
        // 验证 POST_TOOL_USE 配置
        val postToolUseMatchers = options.hooks!![HookEvent.POST_TOOL_USE]!!
        assertEquals(1, postToolUseMatchers.size)
        assertNull(postToolUseMatchers[0].matcher) // 匹配所有工具
        assertEquals(2, postToolUseMatchers[0].hooks.size)
        
        // 验证允许的工具列表包含自定义工具
        assertEquals(9, options.allowedTools.size)
        assertTrue(options.allowedTools.contains("DatabaseQuery"))
        assertTrue(options.allowedTools.contains("APICall"))
        assertTrue(options.allowedTools.contains("DockerCommand"))
        assertTrue(options.allowedTools.contains("FileSystem"))
        assertTrue(options.allowedTools.contains("NetworkTool"))
        
        println("✅ Comprehensive custom tool hook configuration test passed")
    }
    
    @Test
    fun `test async custom tool hooks with complex scenarios`() = runBlocking {
        // 测试异步自定义工具 hooks 与复杂场景
        val asyncValidationHook: HookCallback = { input, toolUseId, context ->
            val toolName = input["tool_name"] as? String ?: ""
            val toolInput = input["tool_input"] as? Map<*, *> ?: emptyMap<String, Any>()
            
            // 模拟异步验证（如外部API调用、数据库查询等）
            delay(10)
            
            when (toolName) {
                "APICall" -> {
                    val endpoint = toolInput["endpoint"] as? String ?: ""
                    // 模拟检查API端点是否在白名单中
                    delay(5)
                    
                    val whitelistedDomains = listOf("api.example.com", "secure.api.com", "trusted.service.io")
                    val isWhitelisted = whitelistedDomains.any { endpoint.contains(it) }
                    
                    if (!isWhitelisted) {
                        HookJSONOutput(
                            decision = "block",
                            systemMessage = "🚫 API endpoint not in whitelist: $endpoint",
                            hookSpecificOutput = JsonPrimitive("endpoint_not_whitelisted")
                        )
                    } else {
                        HookJSONOutput(
                            systemMessage = "✅ API endpoint validated: $endpoint"
                        )
                    }
                }
                "DatabaseQuery" -> {
                    val query = toolInput["query"] as? String ?: ""
                    // 模拟SQL注入检测
                    delay(8)
                    
                    val sqlInjectionPatterns = listOf(
                        "' OR '1'='1",
                        "UNION SELECT",
                        "; DROP TABLE",
                        "' UNION ALL SELECT",
                        "/**/",
                        "xp_cmdshell"
                    )
                    
                    val hasInjection = sqlInjectionPatterns.any { 
                        query.uppercase().contains(it.uppercase()) 
                    }
                    
                    if (hasInjection) {
                        HookJSONOutput(
                            decision = "block",
                            systemMessage = "🚫 Potential SQL injection detected: $query",
                            hookSpecificOutput = JsonPrimitive("sql_injection_detected")
                        )
                    } else {
                        HookJSONOutput(
                            systemMessage = "✅ SQL query validated: $query"
                        )
                    }
                }
                else -> {
                    HookJSONOutput(
                        systemMessage = "✅ Async validation completed for $toolName"
                    )
                }
            }
        }
        
        // 测试场景1：白名单API调用
        val whitelistedAPICall = mapOf(
            "tool_name" to "APICall",
            "tool_input" to mapOf(
                "endpoint" to "https://api.example.com/data",
                "method" to "GET"
            )
        )
        
        val startTime1 = System.currentTimeMillis()
        val result1 = asyncValidationHook(whitelistedAPICall, "api_async_1", HookContext())
        val endTime1 = System.currentTimeMillis()
        
        assertNull(result1.decision)
        assertTrue(result1.systemMessage!!.contains("✅"))
        assertTrue(endTime1 - startTime1 >= 15) // 至少15ms延迟 (10 + 5)
        
        // 测试场景2：非白名单API调用
        val nonWhitelistedAPICall = mapOf(
            "tool_name" to "APICall",
            "tool_input" to mapOf(
                "endpoint" to "https://suspicious.api.net/data",
                "method" to "POST"
            )
        )
        
        val result2 = asyncValidationHook(nonWhitelistedAPICall, "api_async_2", HookContext())
        assertEquals("block", result2.decision)
        assertTrue(result2.systemMessage!!.contains("🚫"))
        assertTrue(result2.systemMessage!!.contains("not in whitelist"))
        assertEquals(JsonPrimitive("endpoint_not_whitelisted"), result2.hookSpecificOutput)
        
        // 测试场景3：安全的SQL查询
        val safeQuery = mapOf(
            "tool_name" to "DatabaseQuery",
            "tool_input" to mapOf(
                "query" to "SELECT name, email FROM users WHERE status = 'active' ORDER BY created_at DESC LIMIT 10"
            )
        )
        
        val result3 = asyncValidationHook(safeQuery, "db_async_1", HookContext())
        assertNull(result3.decision)
        assertTrue(result3.systemMessage!!.contains("✅"))
        assertTrue(result3.systemMessage!!.contains("SQL query validated"))
        
        // 测试场景4：SQL注入尝试
        val maliciousQuery = mapOf(
            "tool_name" to "DatabaseQuery",
            "tool_input" to mapOf(
                "query" to "SELECT * FROM users WHERE id = 1 OR '1'='1'; DROP TABLE users; --"
            )
        )
        
        val result4 = asyncValidationHook(maliciousQuery, "db_async_2", HookContext())
        assertEquals("block", result4.decision)
        assertTrue(result4.systemMessage!!.contains("🚫"))
        assertTrue(result4.systemMessage!!.contains("SQL injection detected"))
        assertEquals(JsonPrimitive("sql_injection_detected"), result4.hookSpecificOutput)
        
        println("✅ Async custom tool hooks with complex scenarios test passed")
    }
    
    @Test
    fun `test custom tool hooks integration with Claude SDK options`() {
        // 测试自定义工具 hooks 与 Claude SDK 选项的完整集成
        val customToolsSecurityHook: HookCallback = { input, toolUseId, context ->
            val toolName = input["tool_name"] as? String ?: ""
            HookJSONOutput(
                systemMessage = "🛡️ Security validated: $toolName",
                hookSpecificOutput = buildJsonObject {
                    put("tool_name", JsonPrimitive(toolName))
                    put("validation_timestamp", JsonPrimitive(System.currentTimeMillis()))
                    put("security_level", JsonPrimitive("high"))
                }
            )
        }
        
        val customToolsAuditHook: HookCallback = { input, toolUseId, context ->
            val toolName = input["tool_name"] as? String ?: ""
            HookJSONOutput(
                systemMessage = "📝 Audit logged: $toolName",
                hookSpecificOutput = buildJsonObject {
                    put("audit_id", JsonPrimitive("audit_${System.currentTimeMillis()}"))
                    put("tool_name", JsonPrimitive(toolName))
                    put("tool_use_id", JsonPrimitive(toolUseId ?: "unknown"))
                }
            )
        }
        
        // 创建完整的SDK配置，包含自定义工具和相应的hooks
        val fullSdkOptions = ClaudeCodeOptions(
            model = "claude-3-haiku-20240307",
            allowedTools = listOf(
                // 核心工具
                "Read", "Write", "Edit", "Bash", "Grep", "Glob",
                // 自定义业务工具
                "DatabaseQuery", "APICall", "DockerCommand", "FileSystem",
                "EmailSender", "PDFGenerator", "ImageProcessor", "DataExporter"
            ),
            permissionMode = PermissionMode.BYPASS_PERMISSIONS,
            hooks = mapOf(
                HookEvent.PRE_TOOL_USE to listOf(
                    // 对所有自定义工具进行安全检查
                    HookMatcher(
                        matcher = "DatabaseQuery|APICall|DockerCommand|FileSystem|EmailSender|PDFGenerator|ImageProcessor|DataExporter",
                        hooks = listOf(customToolsSecurityHook)
                    )
                ),
                HookEvent.POST_TOOL_USE to listOf(
                    // 对所有工具执行进行审计
                    HookMatcher(
                        matcher = null, // 所有工具
                        hooks = listOf(customToolsAuditHook)
                    )
                )
            ),
                你现在可以使用以下自定义工具：
                
                1. DatabaseQuery - 执行数据库查询
                   参数: {"query": "SQL查询语句", "database": "数据库名"}
                
                2. APICall - 调用外部API
                   参数: {"endpoint": "API端点", "method": "HTTP方法", "headers": {}, "data": {}}
                
                3. DockerCommand - 执行Docker命令
                   参数: {"command": "Docker命令", "container": "容器名(可选)"}
                
                4. FileSystem - 文件系统操作
                   参数: {"operation": "read/write/delete", "path": "文件路径", "content": "内容(可选)"}
                
                5. EmailSender - 发送邮件
                   参数: {"to": "收件人", "subject": "主题", "body": "邮件内容"}
                
                6. PDFGenerator - 生成PDF文档
                   参数: {"template": "模板名", "data": {}, "output_path": "输出路径"}
                
                7. ImageProcessor - 处理图像
                   参数: {"operation": "resize/crop/filter", "input_path": "输入路径", "output_path": "输出路径"}
                
                8. DataExporter - 导出数据
                   参数: {"format": "csv/json/xml", "data": {}, "output_path": "输出路径"}
                
                所有工具都会经过安全检查和审计记录。
            """.trimIndent()
        )
        
        // 验证配置的完整性
        assertNotNull(fullSdkOptions.hooks)
        assertNotNull(fullSdkOptions.allowedTools)
        
        // 验证自定义工具数量
        assertEquals(14, fullSdkOptions.allowedTools.size) // 6个标准 + 8个自定义
        
        // 验证hooks配置
        assertEquals(2, fullSdkOptions.hooks!!.size)
        assertTrue(fullSdkOptions.hooks!!.containsKey(HookEvent.PRE_TOOL_USE))
        assertTrue(fullSdkOptions.hooks!!.containsKey(HookEvent.POST_TOOL_USE))
        
        // 验证PRE_TOOL_USE hooks
        val preHooks = fullSdkOptions.hooks!![HookEvent.PRE_TOOL_USE]!!
        assertEquals(1, preHooks.size)
        assertEquals("DatabaseQuery|APICall|DockerCommand|FileSystem|EmailSender|PDFGenerator|ImageProcessor|DataExporter", 
                    preHooks[0].matcher)
        assertEquals(1, preHooks[0].hooks.size)
        
        // 验证POST_TOOL_USE hooks
        val postHooks = fullSdkOptions.hooks!![HookEvent.POST_TOOL_USE]!!
        assertEquals(1, postHooks.size)
        assertNull(postHooks[0].matcher) // 匹配所有工具
        assertEquals(1, postHooks[0].hooks.size)
        
        // 验证系统提示包含自定义工具说明
        
        // 测试hook执行
        val testInput = mapOf(
            "tool_name" to "EmailSender",
            "tool_input" to mapOf(
                "to" to "user@example.com",
                "subject" to "Test Email",
                "body" to "This is a test email"
            )
        )
        
        val securityResult = runBlocking {
            customToolsSecurityHook(testInput, "email_001", HookContext())
        }
        assertTrue(securityResult.systemMessage!!.contains("🛡️ Security validated: EmailSender"))
        assertTrue(securityResult.hookSpecificOutput is JsonObject)
        
        val auditResult = runBlocking {
            customToolsAuditHook(testInput, "email_001", HookContext())
        }
        assertTrue(auditResult.systemMessage!!.contains("📝 Audit logged: EmailSender"))
        assertTrue(auditResult.hookSpecificOutput is JsonObject)
        
        println("✅ Custom tool hooks integration with Claude SDK options test passed")
    }
}