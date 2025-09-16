package com.claudecodeplus.sdk.examples

import com.claudecodeplus.sdk.types.*
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test

/**
 * Claude Code SDK Hooks 简单使用示例
 * 
 * 这个示例展示了如何在实际应用中使用 hooks 功能。
 */
class SimpleHooksExample {

    @Test
    fun `示例1 - 基本安全检查 hooks`() {
        println("=== 示例1：基本安全检查 hooks ===")
        
        // 创建安全检查 hook
        val securityHook: HookCallback = securityHook@{ input, toolUseId, context ->
            val toolName = input["tool_name"] as? String ?: ""
            val toolInput = input["tool_input"] as? Map<*, *> ?: emptyMap<String, Any>()
            
            println("🔒 安全检查: $toolName")
            
            when (toolName) {
                "Bash" -> {
                    val command = toolInput["command"] as? String ?: ""
                    val dangerousCommands = listOf("rm -rf", "sudo", "format")
                    
                    for (dangerous in dangerousCommands) {
                        if (command.contains(dangerous)) {
                            println("🚫 危险命令被阻止: $command")
                            return@securityHook HookJSONOutput(
                                decision = "block",
                                systemMessage = "安全策略: 阻止危险命令 '$dangerous'",
                                hookSpecificOutput = JsonPrimitive("security_block")
                            )
                        }
                    }
                    println("✅ Bash 命令通过安全检查: $command")
                }
                "Write", "Edit" -> {
                    val filePath = toolInput["file_path"] as? String ?: ""
                    val protectedPaths = listOf("/etc/", "/usr/bin/", "/System/")
                    
                    for (protected in protectedPaths) {
                        if (filePath.startsWith(protected)) {
                            println("🚫 受保护文件被阻止: $filePath")
                            return@securityHook HookJSONOutput(
                                decision = "block",
                                systemMessage = "安全策略: 不允许修改系统文件 '$filePath'",
                                hookSpecificOutput = JsonPrimitive("protected_file")
                            )
                        }
                    }
                    println("✅ 文件操作通过安全检查: $filePath")
                }
            }
            
            HookJSONOutput(systemMessage = "✅ 安全检查通过")
        }
        
        // 配置选项
        val options = ClaudeCodeOptions(
            allowedTools = listOf("Read", "Write", "Edit", "Bash", "Grep"),
            hooks = mapOf(
                HookEvent.PRE_TOOL_USE to listOf(
                    HookMatcher(
                        matcher = "Bash|Write|Edit", // 对这些工具进行安全检查
                        hooks = listOf(securityHook)
                    )
                )
            )
        )
        
        println("✅ 安全 hooks 配置完成")
        println("配置了以下安全策略:")
        println("  - 阻止危险 Bash 命令")
        println("  - 保护系统文件")
        println()
        
        // 测试hooks是否正确配置
        assert(options.hooks != null)
        assert(options.hooks!!.containsKey(HookEvent.PRE_TOOL_USE))
        assert(options.hooks!![HookEvent.PRE_TOOL_USE]!!.size == 1)
        assert(options.hooks!![HookEvent.PRE_TOOL_USE]!![0].matcher == "Bash|Write|Edit")
        assert(options.hooks!![HookEvent.PRE_TOOL_USE]!![0].hooks.size == 1)
        
        println("✅ 配置验证通过")
    }

    @Test
    fun `示例2 - 审计日志 hooks`() {
        println("=== 示例2：审计日志 hooks ===")
        
        val auditLog = mutableListOf<String>()
        
        // 创建审计 hook
        val auditHook: HookCallback = { input, toolUseId, context ->
            val toolName = input["tool_name"] as? String ?: ""
            val timestamp = System.currentTimeMillis()
            val auditEntry = "[$timestamp] 工具: $toolName, ID: $toolUseId"
            
            auditLog.add(auditEntry)
            println("📋 审计记录: $auditEntry")
            
            HookJSONOutput(
                systemMessage = "审计已记录",
                hookSpecificOutput = JsonPrimitive("audit_logged")
            )
        }
        
        val options = ClaudeCodeOptions(
            allowedTools = listOf("Read", "Write", "Bash", "Grep", "Glob"),
            hooks = mapOf(
                HookEvent.POST_TOOL_USE to listOf(
                    HookMatcher(
                        matcher = null, // 所有工具
                        hooks = listOf(auditHook)
                    )
                )
            )
        )
        
        println("✅ 审计 hooks 配置完成")
        println("将记录所有工具的使用情况")
        
        // 测试审计功能
        val testInput = mapOf(
            "tool_name" to "Read",
            "tool_input" to mapOf("file_path" to "/tmp/test.txt")
        )
        
        // 在协程中测试
        kotlinx.coroutines.runBlocking {
            val result = auditHook(testInput, "test_001", HookContext())
            assert(result.systemMessage == "审计已记录")
            assert(auditLog.size == 1)
            assert(auditLog[0].contains("工具: Read"))
            assert(auditLog[0].contains("ID: test_001"))
        }
        
        println("✅ 审计功能测试通过")
        println()
    }

    @Test
    fun `示例3 - 自定义工具验证 hooks`() {
        println("=== 示例3：自定义工具验证 hooks ===")
        
        // 数据库查询验证 hook
        val databaseHook: HookCallback = databaseHook@{ input, toolUseId, context ->
            val toolName = input["tool_name"] as? String ?: ""
            val toolInput = input["tool_input"] as? Map<*, *> ?: emptyMap<String, Any>()
            
            if (toolName == "DatabaseQuery") {
                val query = toolInput["query"] as? String ?: ""
                val database = toolInput["database"] as? String ?: ""
                
                println("🗃️ 数据库查询验证: $database")
                println("   SQL: $query")
                
                // 检查危险SQL操作
                val dangerousOperations = listOf("DROP", "DELETE", "TRUNCATE", "ALTER")
                for (operation in dangerousOperations) {
                    if (query.uppercase().contains(operation)) {
                        println("🚫 危险SQL操作被阻止: $operation")
                        return@databaseHook HookJSONOutput(
                            decision = "block",
                            systemMessage = "数据库安全: 不允许 $operation 操作",
                            hookSpecificOutput = JsonPrimitive("dangerous_sql")
                        )
                    }
                }
                
                println("✅ 数据库查询验证通过")
            }
            
            HookJSONOutput(systemMessage = "数据库验证完成")
        }
        
        val options = ClaudeCodeOptions(
            allowedTools = listOf(
                "Read", "Write", "Bash",
                "DatabaseQuery", "APICall"
            ),
            hooks = mapOf(
                HookEvent.PRE_TOOL_USE to listOf(
                    HookMatcher(
                        matcher = "DatabaseQuery",
                        hooks = listOf(databaseHook)
                    )
                )
            ),
            appendSystemPrompt = """
                可用的自定义工具:
                1. DatabaseQuery - 执行数据库查询
                2. APICall - 调用外部API
                
                所有自定义工具都会经过安全验证。
            """.trimIndent()
        )
        
        println("✅ 自定义工具 hooks 配置完成")
        
        // 在协程中测试数据库验证
        kotlinx.coroutines.runBlocking {
            // 测试安全的数据库查询
            val safeQuery = mapOf(
                "tool_name" to "DatabaseQuery",
                "tool_input" to mapOf(
                    "query" to "SELECT * FROM users WHERE active = 1",
                    "database" to "main"
                )
            )
            
            val safeResult = databaseHook(safeQuery, "db_safe", HookContext())
            assert(safeResult.decision == null)
            assert(safeResult.systemMessage == "数据库验证完成")
            
            // 测试危险的数据库查询
            val dangerousQuery = mapOf(
                "tool_name" to "DatabaseQuery",
                "tool_input" to mapOf(
                    "query" to "DROP TABLE users",
                    "database" to "main"
                )
            )
            
            val dangerousResult = databaseHook(dangerousQuery, "db_danger", HookContext())
            assert(dangerousResult.decision == "block")
            assert(dangerousResult.systemMessage!!.contains("不允许 DROP 操作"))
            assert(dangerousResult.hookSpecificOutput == JsonPrimitive("dangerous_sql"))
        }
        
        println("✅ 自定义工具验证测试通过")
        println()
    }

    @Test
    fun `示例4 - 完整配置示例`() {
        println("=== 示例4：完整配置示例 ===")
        
        val securityHook: HookCallback = { input, _, _ ->
            val toolName = input["tool_name"] as? String ?: ""
            HookJSONOutput(systemMessage = "🔒 安全检查: $toolName")
        }
        
        val auditHook: HookCallback = { input, _, _ ->
            val toolName = input["tool_name"] as? String ?: ""
            HookJSONOutput(systemMessage = "📋 审计记录: $toolName")
        }
        
        val performanceHook: HookCallback = { input, _, _ ->
            val toolName = input["tool_name"] as? String ?: ""
            HookJSONOutput(systemMessage = "⚡ 性能监控: $toolName")
        }
        
        // 配置所有6种 hook 事件类型
        val fullHooksConfig = mapOf(
            HookEvent.PRE_TOOL_USE to listOf(
                HookMatcher(
                    matcher = "Bash|Write|Edit",
                    hooks = listOf(securityHook)
                )
            ),
            HookEvent.POST_TOOL_USE to listOf(
                HookMatcher(
                    matcher = null, // 所有工具
                    hooks = listOf(auditHook)
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
            model = "claude-3-haiku-20240307",
            allowedTools = listOf(
                "Read", "Write", "Edit", "Bash", "Grep", "Glob",
                "DatabaseQuery", "APICall", "EmailSender"
            ),
            permissionMode = PermissionMode.BYPASS_PERMISSIONS,
            hooks = fullHooksConfig
        )
        
        println("✅ 完整 hooks 配置完成")
        println("配置的功能:")
        println("  🔒 工具安全检查")
        println("  📋 全面审计记录")
        println("  ⚡ 性能监控")
        println()
        
        // 验证配置
        assert(options.hooks != null)
        assert(options.hooks!!.size == 6)
        assert(options.hooks!!.containsKey(HookEvent.PRE_TOOL_USE))
        assert(options.hooks!!.containsKey(HookEvent.POST_TOOL_USE))
        assert(options.hooks!!.containsKey(HookEvent.USER_PROMPT_SUBMIT))
        assert(options.hooks!!.containsKey(HookEvent.STOP))
        assert(options.hooks!!.containsKey(HookEvent.SUBAGENT_STOP))
        assert(options.hooks!!.containsKey(HookEvent.PRE_COMPACT))
        
        println("✅ 配置验证通过，共配置了 ${options.hooks!!.size} 种事件类型")
        println("✅ 允许使用 ${options.allowedTools.size} 个工具")
    }
}