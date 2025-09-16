package com.claudecodeplus.sdk

import com.claudecodeplus.sdk.types.*
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach

/**
 * MCP Server 自定义工具功能测试
 * 验证 MCP (Model Context Protocol) 服务器的配置、集成和自定义工具功能
 */
class McpServerTest {

    @Test
    fun `测试 MCP Server 配置类型`() {
        println("=== 测试 MCP Server 配置类型 ===")
        
        // 测试 stdio 服务器配置
        val stdioConfig = McpStdioServerConfig(
            command = "node",
            args = listOf("my-mcp-server.js"),
            env = mapOf("DEBUG" to "1", "PORT" to "3000")
        )
        
        assertEquals("stdio", stdioConfig.type)
        assertEquals("node", stdioConfig.command)
        assertEquals(listOf("my-mcp-server.js"), stdioConfig.args)
        assertEquals(mapOf("DEBUG" to "1", "PORT" to "3000"), stdioConfig.env)
        
        // 测试 SSE 服务器配置
        val sseConfig = McpSSEServerConfig(
            url = "https://api.example.com/mcp",
            headers = mapOf("Authorization" to "Bearer token123", "Content-Type" to "application/json")
        )
        
        assertEquals("sse", sseConfig.type)
        assertEquals("https://api.example.com/mcp", sseConfig.url)
        assertTrue(sseConfig.headers.containsKey("Authorization"))
        
        // 测试 HTTP 服务器配置
        val httpConfig = McpHttpServerConfig(
            url = "http://localhost:8080/mcp",
            headers = mapOf("X-API-Key" to "secret123")
        )
        
        assertEquals("http", httpConfig.type)
        assertEquals("http://localhost:8080/mcp", httpConfig.url)
        assertEquals("secret123", httpConfig.headers["X-API-Key"])
        
        println("✅ MCP Server 配置类型测试通过")
    }

    @Test
    fun `测试 ClaudeCodeOptions 中的 MCP 集成`() {
        println("=== 测试 ClaudeCodeOptions 中的 MCP 集成 ===")
        
        // 创建多种类型的 MCP 服务器配置
        val mcpServers = mapOf(
            "database-tools" to McpStdioServerConfig(
                command = "python",
                args = listOf("-m", "database_mcp_server"),
                env = mapOf("DB_CONNECTION" to "postgresql://localhost:5432/mydb")
            ),
            "web-scraper" to McpSSEServerConfig(
                url = "https://scraper-service.com/mcp",
                headers = mapOf("API-Key" to "scraper-key-123")
            ),
            "file-processor" to McpHttpServerConfig(
                url = "http://fileprocessor:8080/mcp",
                headers = mapOf("Authorization" to "Bearer file-token")
            )
        )
        
        val options = ClaudeCodeOptions(
            model = "claude-3-5-sonnet-20241022",
            allowedTools = listOf("Read", "Write", "Bash", "mcp__database-tools__query", "mcp__web-scraper__scrape"),
            mcpServers = mcpServers,
            appendSystemPrompt = """
                可用的 MCP 服务器工具：
                1. database-tools - 数据库查询工具
                2. web-scraper - 网页抓取工具
                3. file-processor - 文件处理工具
            """.trimIndent()
        )
        
        // 验证配置
        assertEquals(3, options.mcpServers.size)
        assertTrue(options.mcpServers.containsKey("database-tools"))
        assertTrue(options.mcpServers.containsKey("web-scraper"))
        assertTrue(options.mcpServers.containsKey("file-processor"))
        
        // 验证stdio配置
        val dbConfig = options.mcpServers["database-tools"] as McpStdioServerConfig
        assertEquals("python", dbConfig.command)
        assertEquals(listOf("-m", "database_mcp_server"), dbConfig.args)
        
        // 验证SSE配置
        val scraperConfig = options.mcpServers["web-scraper"] as McpSSEServerConfig
        assertEquals("https://scraper-service.com/mcp", scraperConfig.url)
        
        // 验证HTTP配置
        val fileConfig = options.mcpServers["file-processor"] as McpHttpServerConfig
        assertEquals("http://fileprocessor:8080/mcp", fileConfig.url)
        
        // 验证允许的工具包含MCP工具
        assertTrue(options.allowedTools.contains("mcp__database-tools__query"))
        assertTrue(options.allowedTools.contains("mcp__web-scraper__scrape"))
        
        println("✅ MCP 集成配置测试通过")
    }

    @Test
    fun `测试 MCP 工具命名约定`() {
        println("=== 测试 MCP 工具命名约定 ===")
        
        // MCP 工具遵循格式: mcp__{server_name}__{tool_name}
        val mcpToolNames = listOf(
            "mcp__database-tools__query",
            "mcp__database-tools__execute",
            "mcp__database-tools__migrate",
            "mcp__web-scraper__scrape",
            "mcp__web-scraper__extract",
            "mcp__file-processor__convert",
            "mcp__file-processor__compress",
            "mcp__ai-agent__generate",
            "mcp__ai-agent__analyze"
        )
        
        val mcpServerNames = mutableSetOf<String>()
        val mcpToolsByServer = mutableMapOf<String, MutableList<String>>()
        
        // 解析 MCP 工具名称
        mcpToolNames.forEach { toolName ->
            if (toolName.startsWith("mcp__")) {
                val parts = toolName.split("__")
                if (parts.size >= 3) {
                    val serverName = parts[1]
                    val actualToolName = parts.drop(2).joinToString("__")
                    
                    mcpServerNames.add(serverName)
                    mcpToolsByServer.getOrPut(serverName) { mutableListOf() }.add(actualToolName)
                    
                    println("🔧 服务器: $serverName, 工具: $actualToolName")
                }
            }
        }
        
        // 验证解析结果
        assertEquals(4, mcpServerNames.size)
        assertTrue(mcpServerNames.contains("database-tools"))
        assertTrue(mcpServerNames.contains("web-scraper"))
        assertTrue(mcpServerNames.contains("file-processor"))
        assertTrue(mcpServerNames.contains("ai-agent"))
        
        assertEquals(3, mcpToolsByServer["database-tools"]?.size)
        assertEquals(2, mcpToolsByServer["web-scraper"]?.size)
        assertEquals(2, mcpToolsByServer["file-processor"]?.size)
        assertEquals(2, mcpToolsByServer["ai-agent"]?.size)
        
        assertTrue(mcpToolsByServer["database-tools"]?.contains("query") == true)
        assertTrue(mcpToolsByServer["web-scraper"]?.contains("scrape") == true)
        
        println("✅ MCP 工具命名约定测试通过")
    }

    @Test
    fun `测试 MCP 服务器与 hooks 的集成`() {
        println("=== 测试 MCP 服务器与 hooks 的集成 ===")
        
        val mcpToolSecurityHook: HookCallback = mcpHook@{ input, toolUseId, context ->
            val toolName = input["tool_name"] as? String ?: ""
            val toolInput = input["tool_input"] as? Map<*, *> ?: emptyMap<String, Any>()
            
            println("🔒 MCP 工具安全检查: $toolName")
            
            // 检查是否为 MCP 工具
            if (toolName.startsWith("mcp__")) {
                val parts = toolName.split("__")
                if (parts.size >= 3) {
                    val serverName = parts[1]
                    val actualToolName = parts.drop(2).joinToString("__")
                    
                    println("   服务器: $serverName")
                    println("   工具: $actualToolName")
                    
                    // 对不同的 MCP 服务器应用不同的安全策略
                    when (serverName) {
                        "database-tools" -> {
                            val query = toolInput["query"] as? String ?: ""
                            val dangerousOps = listOf("DROP", "DELETE", "TRUNCATE", "UPDATE")
                            
                            for (op in dangerousOps) {
                                if (query.uppercase().contains(op)) {
                                    println("🚫 阻止危险数据库操作: $op")
                                    return@mcpHook HookJSONOutput(
                                        decision = "block",
                                        systemMessage = "MCP安全策略: 阻止危险数据库操作 '$op'",
                                        hookSpecificOutput = JsonPrimitive("mcp_db_security_block")
                                    )
                                }
                            }
                        }
                        "web-scraper" -> {
                            val url = toolInput["url"] as? String ?: ""
                            val blockedDomains = listOf("admin.", "internal.", "localhost")
                            
                            for (domain in blockedDomains) {
                                if (url.contains(domain)) {
                                    println("🚫 阻止访问受限域名: $domain")
                                    return@mcpHook HookJSONOutput(
                                        decision = "block",
                                        systemMessage = "MCP安全策略: 阻止访问受限域名 '$domain'",
                                        hookSpecificOutput = JsonPrimitive("mcp_web_security_block")
                                    )
                                }
                            }
                        }
                        "file-processor" -> {
                            val filePath = toolInput["file_path"] as? String ?: ""
                            val protectedPaths = listOf("/etc/", "/usr/bin/", "/System/")
                            
                            for (path in protectedPaths) {
                                if (filePath.startsWith(path)) {
                                    println("🚫 阻止访问系统文件: $path")
                                    return@mcpHook HookJSONOutput(
                                        decision = "block",
                                        systemMessage = "MCP安全策略: 阻止访问系统文件 '$path'",
                                        hookSpecificOutput = JsonPrimitive("mcp_file_security_block")
                                    )
                                }
                            }
                        }
                    }
                    
                    println("✅ MCP 工具安全检查通过")
                    return@mcpHook HookJSONOutput(
                        systemMessage = "MCP工具 $toolName 通过安全检查",
                        hookSpecificOutput = buildJsonObject {
                            put("mcp_server", JsonPrimitive(serverName))
                            put("mcp_tool", JsonPrimitive(actualToolName))
                            put("security_status", JsonPrimitive("approved"))
                        }
                    )
                }
            }
            
            HookJSONOutput(systemMessage = "非MCP工具，跳过MCP安全检查")
        }
        
        val mcpAuditHook: HookCallback = { input, toolUseId, context ->
            val toolName = input["tool_name"] as? String ?: ""
            
            if (toolName.startsWith("mcp__")) {
                val parts = toolName.split("__")
                val serverName = if (parts.size >= 2) parts[1] else "unknown"
                val actualToolName = if (parts.size >= 3) parts.drop(2).joinToString("__") else "unknown"
                
                println("📋 MCP 工具审计: 服务器=$serverName, 工具=$actualToolName")
                
                HookJSONOutput(
                    systemMessage = "MCP工具使用已记录",
                    hookSpecificOutput = buildJsonObject {
                        put("audit_type", JsonPrimitive("mcp_tool_usage"))
                        put("server_name", JsonPrimitive(serverName))
                        put("tool_name", JsonPrimitive(actualToolName))
                        put("timestamp", JsonPrimitive(System.currentTimeMillis()))
                    }
                )
            } else {
                HookJSONOutput(systemMessage = "非MCP工具审计")
            }
        }
        
        // 配置 MCP 服务器和相应的 hooks
        val options = ClaudeCodeOptions(
            mcpServers = mapOf(
                "database-tools" to McpStdioServerConfig(command = "python", args = listOf("-m", "db_server")),
                "web-scraper" to McpSSEServerConfig(url = "https://scraper.com/mcp"),
                "file-processor" to McpHttpServerConfig(url = "http://fileproc:8080/mcp")
            ),
            allowedTools = listOf(
                "Read", "Write",
                "mcp__database-tools__query",
                "mcp__web-scraper__scrape",
                "mcp__file-processor__convert"
            ),
            hooks = mapOf(
                HookEvent.PRE_TOOL_USE to listOf(
                    HookMatcher(
                        matcher = "mcp__.*", // 匹配所有 MCP 工具
                        hooks = listOf(mcpToolSecurityHook)
                    )
                ),
                HookEvent.POST_TOOL_USE to listOf(
                    HookMatcher(
                        matcher = "mcp__.*",
                        hooks = listOf(mcpAuditHook)
                    )
                )
            )
        )
        
        // 验证配置
        assertNotNull(options.mcpServers)
        assertNotNull(options.hooks)
        assertEquals(3, options.mcpServers.size)
        assertEquals(2, options.hooks!!.size)
        
        // 测试安全 hook
        kotlinx.coroutines.runBlocking {
            // 测试安全的数据库查询
            val safeDbInput = mapOf(
                "tool_name" to "mcp__database-tools__query",
                "tool_input" to mapOf("query" to "SELECT * FROM users WHERE active = 1")
            )
            
            val safeResult = mcpToolSecurityHook(safeDbInput, "mcp_db_1", HookContext())
            assertNull(safeResult.decision)
            assertTrue(safeResult.systemMessage!!.contains("通过安全检查"))
            
            // 测试危险的数据库操作
            val dangerousDbInput = mapOf(
                "tool_name" to "mcp__database-tools__execute",
                "tool_input" to mapOf("query" to "DROP TABLE users")
            )
            
            val dangerousResult = mcpToolSecurityHook(dangerousDbInput, "mcp_db_2", HookContext())
            assertEquals("block", dangerousResult.decision)
            assertTrue(dangerousResult.systemMessage!!.contains("阻止危险数据库操作"))
            
            // 测试网页抓取工具
            val webInput = mapOf(
                "tool_name" to "mcp__web-scraper__scrape",
                "tool_input" to mapOf("url" to "https://admin.internal.com/data")
            )
            
            val webResult = mcpToolSecurityHook(webInput, "mcp_web_1", HookContext())
            assertEquals("block", webResult.decision)
            assertTrue(webResult.systemMessage!!.contains("阻止访问受限域名"))
            
            // 测试审计 hook
            val auditResult = mcpAuditHook(safeDbInput, "mcp_db_1", HookContext())
            assertTrue(auditResult.systemMessage!!.contains("MCP工具使用已记录"))
        }
        
        println("✅ MCP 服务器与 hooks 集成测试通过")
    }

    @Test
    fun `测试复杂的 MCP 服务器场景`() {
        println("=== 测试复杂的 MCP 服务器场景 ===")
        
        // 模拟真实的企业级 MCP 服务器配置
        val enterpriseMcpConfig = mapOf(
            // 生产数据库服务器
            "prod-database" to McpStdioServerConfig(
                command = "python",
                args = listOf("-m", "enterprise_db_mcp"),
                env = mapOf(
                    "DB_HOST" to "prod-db.company.com",
                    "DB_PORT" to "5432",
                    "DB_NAME" to "production",
                    "SSL_MODE" to "require"
                )
            ),
            
            // 开发环境数据库
            "dev-database" to McpStdioServerConfig(
                command = "python",
                args = listOf("-m", "dev_db_mcp"),
                env = mapOf(
                    "DB_HOST" to "dev-db.company.com",
                    "DB_PORT" to "5432",
                    "DB_NAME" to "development"
                )
            ),
            
            // 微服务API网关
            "api-gateway" to McpSSEServerConfig(
                url = "https://api-gateway.company.com/mcp",
                headers = mapOf(
                    "Authorization" to "Bearer \${API_TOKEN}",
                    "X-Service-Name" to "claude-code-plus",
                    "X-Environment" to "production"
                )
            ),
            
            // 文件存储服务
            "file-storage" to McpHttpServerConfig(
                url = "http://file-service.company.internal:8080/mcp",
                headers = mapOf(
                    "X-API-Key" to "\${FILE_SERVICE_KEY}",
                    "Content-Type" to "application/json"
                )
            ),
            
            // 机器学习服务
            "ml-service" to McpSSEServerConfig(
                url = "https://ml-api.company.com/mcp/stream",
                headers = mapOf(
                    "Authorization" to "Bearer \${ML_TOKEN}",
                    "X-Model-Version" to "v2.1"
                )
            )
        )
        
        // 复杂的权限和安全 hook
        val enterpriseSecurityHook: HookCallback = enterpriseHook@{ input, toolUseId, context ->
            val toolName = input["tool_name"] as? String ?: ""
            val toolInput = input["tool_input"] as? Map<*, *> ?: emptyMap<String, Any>()
            
            if (!toolName.startsWith("mcp__")) {
                return@enterpriseHook HookJSONOutput(systemMessage = "非MCP工具")
            }
            
            val parts = toolName.split("__")
            if (parts.size < 3) {
                return@enterpriseHook HookJSONOutput(
                    decision = "block",
                    systemMessage = "无效的MCP工具名称格式"
                )
            }
            
            val serverName = parts[1]
            val actualToolName = parts.drop(2).joinToString("__")
            
            println("🏢 企业安全检查: 服务器=$serverName, 工具=$actualToolName")
            
            when (serverName) {
                "prod-database" -> {
                    // 生产数据库需要最高级别的安全检查
                    val query = toolInput["query"] as? String ?: ""
                    val readOnlyOps = listOf("SELECT", "SHOW", "DESCRIBE", "EXPLAIN")
                    val hasReadOnlyOp = readOnlyOps.any { query.uppercase().trim().startsWith(it) }
                    
                    if (!hasReadOnlyOp) {
                        return@enterpriseHook HookJSONOutput(
                            decision = "block",
                            systemMessage = "生产数据库仅允许只读操作",
                            hookSpecificOutput = JsonPrimitive("prod_db_write_blocked")
                        )
                    }
                    
                    // 检查敏感表
                    val sensitiveTables = listOf("users", "payments", "credentials", "secrets")
                    for (table in sensitiveTables) {
                        if (query.lowercase().contains(table)) {
                            return@enterpriseHook HookJSONOutput(
                                decision = "block",
                                systemMessage = "禁止访问敏感表: $table",
                                hookSpecificOutput = JsonPrimitive("sensitive_table_blocked")
                            )
                        }
                    }
                }
                
                "dev-database" -> {
                    // 开发数据库相对宽松，但仍有限制
                    val query = toolInput["query"] as? String ?: ""
                    if (query.uppercase().contains("DROP DATABASE") || 
                        query.uppercase().contains("DROP SCHEMA")) {
                        return@enterpriseHook HookJSONOutput(
                            decision = "block",
                            systemMessage = "开发环境禁止删除整个数据库/模式",
                            hookSpecificOutput = JsonPrimitive("dev_db_schema_drop_blocked")
                        )
                    }
                }
                
                "api-gateway" -> {
                    val endpoint = toolInput["endpoint"] as? String ?: ""
                    val method = toolInput["method"] as? String ?: "GET"
                    
                    // 限制对管理端点的访问
                    if (endpoint.contains("/admin/") && method != "GET") {
                        return@enterpriseHook HookJSONOutput(
                            decision = "block",
                            systemMessage = "禁止对管理端点进行非GET操作",
                            hookSpecificOutput = JsonPrimitive("admin_endpoint_blocked")
                        )
                    }
                    
                    // 限制批量操作
                    if (endpoint.contains("/batch/") && method == "DELETE") {
                        return@enterpriseHook HookJSONOutput(
                            decision = "block",
                            systemMessage = "禁止批量删除操作",
                            hookSpecificOutput = JsonPrimitive("batch_delete_blocked")
                        )
                    }
                }
                
                "file-storage" -> {
                    val filePath = toolInput["file_path"] as? String ?: ""
                    val operation = toolInput["operation"] as? String ?: ""
                    
                    // 系统文件保护
                    val protectedPaths = listOf("/system/", "/config/", "/secrets/")
                    for (path in protectedPaths) {
                        if (filePath.startsWith(path)) {
                            return@enterpriseHook HookJSONOutput(
                                decision = "block",
                                systemMessage = "禁止访问系统路径: $path",
                                hookSpecificOutput = JsonPrimitive("system_path_blocked")
                            )
                        }
                    }
                    
                    // 限制删除操作
                    if (operation == "delete" && !filePath.startsWith("/tmp/")) {
                        return@enterpriseHook HookJSONOutput(
                            decision = "block",
                            systemMessage = "仅允许删除临时文件",
                            hookSpecificOutput = JsonPrimitive("delete_restricted")
                        )
                    }
                }
                
                "ml-service" -> {
                    val modelName = toolInput["model"] as? String ?: ""
                    val dataSize = toolInput["data_size"] as? Int ?: 0
                    
                    // 限制大数据处理
                    if (dataSize > 10000) {
                        return@enterpriseHook HookJSONOutput(
                            decision = "block",
                            systemMessage = "数据量超出限制 (最大10000条记录)",
                            hookSpecificOutput = JsonPrimitive("data_size_exceeded")
                        )
                    }
                    
                    // 限制生产模型的使用
                    if (modelName.contains("prod") || modelName.contains("production")) {
                        return@enterpriseHook HookJSONOutput(
                            decision = "block",
                            systemMessage = "禁止直接使用生产ML模型",
                            hookSpecificOutput = JsonPrimitive("prod_model_blocked")
                        )
                    }
                }
            }
            
            HookJSONOutput(
                systemMessage = "企业安全检查通过",
                hookSpecificOutput = buildJsonObject {
                    put("enterprise_check", JsonPrimitive("approved"))
                    put("server", JsonPrimitive(serverName))
                    put("tool", JsonPrimitive(actualToolName))
                    put("security_level", JsonPrimitive("enterprise"))
                }
            )
        }
        
        val options = ClaudeCodeOptions(
            model = "claude-3-5-sonnet-20241022",
            mcpServers = enterpriseMcpConfig,
            allowedTools = listOf(
                "Read", "Write", "Edit", "Bash",
                // 生产数据库工具（只读）
                "mcp__prod-database__query",
                "mcp__prod-database__explain",
                // 开发数据库工具（读写）
                "mcp__dev-database__query", 
                "mcp__dev-database__execute",
                "mcp__dev-database__migrate",
                // API网关工具
                "mcp__api-gateway__get",
                "mcp__api-gateway__post",
                "mcp__api-gateway__health_check",
                // 文件服务工具
                "mcp__file-storage__read",
                "mcp__file-storage__write", 
                "mcp__file-storage__delete",
                // ML服务工具
                "mcp__ml-service__predict",
                "mcp__ml-service__train",
                "mcp__ml-service__evaluate"
            ),
            hooks = mapOf(
                HookEvent.PRE_TOOL_USE to listOf(
                    HookMatcher(
                        matcher = "mcp__.*",
                        hooks = listOf(enterpriseSecurityHook)
                    )
                )
            ),
            appendSystemPrompt = """
                🏢 企业级 MCP 服务器环境已配置：
                
                📊 可用服务器:
                - prod-database: 生产数据库（只读）
                - dev-database: 开发数据库（读写）
                - api-gateway: API网关服务
                - file-storage: 文件存储服务
                - ml-service: 机器学习服务
                
                🔒 安全策略:
                - 生产数据库仅允许只读查询
                - 禁止访问敏感表和系统路径
                - API网关管理端点受限
                - 文件删除操作受限
                - ML服务数据量限制
                
                所有 MCP 工具调用都会经过企业级安全审查。
            """.trimIndent()
        )
        
        // 验证企业配置
        assertEquals(5, options.mcpServers.size)
        assertEquals(14, options.allowedTools.filter { it.startsWith("mcp__") }.size)
        assertTrue(options.appendSystemPrompt!!.contains("企业级"))
        
        // 测试企业安全 hook
        kotlinx.coroutines.runBlocking {
            // 测试生产数据库只读查询（应该通过）
            val prodReadQuery = mapOf(
                "tool_name" to "mcp__prod-database__query",
                "tool_input" to mapOf("query" to "SELECT count(*) FROM orders WHERE status = 'completed'")
            )
            val readResult = enterpriseSecurityHook(prodReadQuery, "prod_1", HookContext())
            assertNull(readResult.decision)
            
            // 测试生产数据库写操作（应该被阻止）
            val prodWriteQuery = mapOf(
                "tool_name" to "mcp__prod-database__execute",
                "tool_input" to mapOf("query" to "UPDATE users SET status = 'inactive'")
            )
            val writeResult = enterpriseSecurityHook(prodWriteQuery, "prod_2", HookContext())
            assertEquals("block", writeResult.decision)
            assertTrue(writeResult.systemMessage!!.contains("仅允许只读操作"))
            
            // 测试访问敏感表（应该被阻止）
            val sensitiveQuery = mapOf(
                "tool_name" to "mcp__prod-database__query",
                "tool_input" to mapOf("query" to "SELECT * FROM users WHERE role = 'admin'")
            )
            val sensitiveResult = enterpriseSecurityHook(sensitiveQuery, "prod_3", HookContext())
            assertEquals("block", sensitiveResult.decision)
            assertTrue(sensitiveResult.systemMessage!!.contains("禁止访问敏感表"))
            
            // 测试ML服务数据量限制
            val largeMlQuery = mapOf(
                "tool_name" to "mcp__ml-service__train",
                "tool_input" to mapOf(
                    "model" to "test-model",
                    "data_size" to 50000
                )
            )
            val mlResult = enterpriseSecurityHook(largeMlQuery, "ml_1", HookContext())
            assertEquals("block", mlResult.decision)
            assertTrue(mlResult.systemMessage!!.contains("数据量超出限制"))
        }
        
        println("✅ 复杂企业级 MCP 服务器场景测试通过")
    }
}