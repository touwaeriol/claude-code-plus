package com.asakii.claude.agent.sdk.builders


import com.asakii.claude.agent.sdk.types.*
import kotlinx.serialization.json.*

/**
 * Hook构建器 - 提供更便捷的Hook定义方式
 * 
 * 使用示例：
 * ```kotlin
 * val hooks = hookBuilder {
 *     // 安全Hook
 *     onPreToolUse("Bash") { toolCall ->
 *         val command = toolCall.getStringParam("command")
 *         if (command.contains("rm -rf")) {
 *             block("危险命令被阻止: $command")
 *         } else {
 *             allow("安全检查通过")
 *         }
 *     }
 *     
 *     // 统计Hook
 *     onPreToolUse(".*") { toolCall ->
 *         println("工具调用: ${toolCall.toolName}")
 *         allow("统计完成")
 *     }
 * }
 * ```
 */
class HookBuilder {
    private val hooks = mutableMapOf<HookEvent, MutableList<HookMatcher>>()
    
    /**
     * 便捷的工具调用信息包装类
     */
    class ToolCall(
        val toolName: String,
        val toolUseId: String?,
        val input: Map<String, Any>,
        val context: HookContext
    ) {
        fun getStringParam(name: String): String = input[name] as? String ?: ""
        fun getNumberParam(name: String): Double = (input[name] as? Number)?.toDouble() ?: 0.0
        fun getBooleanParam(name: String): Boolean = input[name] as? Boolean ?: false
        fun getMapParam(name: String): Map<*, *> = input[name] as? Map<*, *> ?: emptyMap<String, Any>()
        
        override fun toString(): String = "ToolCall(name='$toolName', params=$input)"
    }
    
    /**
     * Hook结果构建器
     */
    class HookResult {
        fun allow(message: String = ""): HookJSONOutput {
            return HookJSONOutput(systemMessage = message)
        }
        
        fun block(message: String, output: JsonElement = JsonPrimitive("blocked")): HookJSONOutput {
            return HookJSONOutput(
                decision = "block",
                systemMessage = message,
                hookSpecificOutput = output
            )
        }
        
        fun interrupt(message: String): HookJSONOutput {
            return HookJSONOutput(
                decision = "block", 
                systemMessage = message,
                hookSpecificOutput = JsonPrimitive("interrupted")
            )
        }
    }
    
    /**
     * PRE_TOOL_USE Hook
     */
    fun onPreToolUse(matcher: String, handler: HookResult.(ToolCall) -> HookJSONOutput) {
        addHook(HookEvent.PRE_TOOL_USE, matcher, handler)
    }
    
    /**
     * POST_TOOL_USE Hook
     */
    fun onPostToolUse(matcher: String, handler: HookResult.(ToolCall) -> HookJSONOutput) {
        addHook(HookEvent.POST_TOOL_USE, matcher, handler)
    }
    
    /**
     * USER_PROMPT_SUBMIT Hook
     */
    fun onUserPromptSubmit(handler: HookResult.(ToolCall) -> HookJSONOutput) {
        addHook(HookEvent.USER_PROMPT_SUBMIT, null, handler)
    }
    
    private fun addHook(
        event: HookEvent, 
        matcher: String?, 
        handler: HookResult.(ToolCall) -> HookJSONOutput
    ) {
        val hookCallback: HookCallback = { input, toolUseId, context ->
            val toolName = input["tool_name"] as? String ?: ""
            val toolInput = input["tool_input"] as? Map<*, *> ?: emptyMap<String, Any>()
            
            val toolCall = ToolCall(
                toolName = toolName,
                toolUseId = toolUseId,
                input = toolInput as Map<String, Any>,
                context = context
            )
            
            val result = HookResult()
            result.handler(toolCall)
        }
        
        val hookMatcher = HookMatcher(
            matcher = matcher,
            hooks = listOf(hookCallback)
        )
        
        hooks.getOrPut(event) { mutableListOf() }.add(hookMatcher)
    }
    
    fun build(): Map<HookEvent, List<HookMatcher>> = hooks
}

/**
 * DSL入口函数
 */
fun hookBuilder(init: HookBuilder.() -> Unit): Map<HookEvent, List<HookMatcher>> {
    return HookBuilder().apply(init).build()
}

/**
 * 快捷安全Hook构建器
 */
fun securityHook(
    dangerousPatterns: List<String> = listOf("rm -rf", "sudo", "format", "delete"),
    allowedCommands: List<String> = emptyList()
): Map<HookEvent, List<HookMatcher>> = hookBuilder {
    onPreToolUse("Bash") { toolCall ->
        val command = toolCall.getStringParam("command")
        
        // 检查允许列表
        if (allowedCommands.any { command.contains(it, ignoreCase = true) }) {
            return@onPreToolUse allow("✅ 命令在允许列表中")
        }
        
        // 检查危险模式
        for (pattern in dangerousPatterns) {
            if (command.contains(pattern, ignoreCase = true)) {
                return@onPreToolUse block("🚫 安全策略阻止危险命令: $pattern")
            }
        }
        
        allow("✅ 安全检查通过")
    }
}

/**
 * 快捷统计Hook构建器
 */
fun statisticsHook(): Map<HookEvent, List<HookMatcher>> {
    var callCount = 0
    val toolStats = mutableMapOf<String, Int>()
    
    return hookBuilder {
        onPreToolUse(".*") { toolCall ->
            callCount++
            toolStats[toolCall.toolName] = toolStats.getOrDefault(toolCall.toolName, 0) + 1
            
            println("📊 [统计] 第 $callCount 次工具调用: ${toolCall.toolName}")
            println("📊 [统计] 工具使用统计: $toolStats")
            
            allow("📊 统计: 总计 $callCount 次，${toolCall.toolName} 第 ${toolStats[toolCall.toolName]} 次")
        }
    }
}
