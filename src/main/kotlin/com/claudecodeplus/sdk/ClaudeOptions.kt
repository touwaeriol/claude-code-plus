package com.claudecodeplus.sdk

/**
 * Claude SDK 选项配置类
 * 对应 Claude SDK 的 options 参数
 */
data class ClaudeOptions(
    /**
     * 系统提示词
     */
    val systemPrompt: String? = null,
    
    /**
     * 最大对话轮数
     */
    val maxTurns: Int? = null,
    
    /**
     * 工作目录路径
     */
    val cwd: String? = null,
    
    /**
     * 允许使用的工具列表
     * 例如: ["read", "write", "bash"]
     */
    val allowedTools: List<String>? = null,
    
    /**
     * 权限模式
     * 例如: "auto", "manual"
     */
    val permissionMode: String? = null,
    
    /**
     * 最大思考令牌数
     */
    val maxThinkingTokens: Int? = null,
    
    /**
     * 模型名称
     * 支持: "Opus", "Sonnet"
     */
    val model: String? = null,
    
    /**
     * MCP 服务配置
     * 格式可以是单个服务配置或包含 mcpServers 的多服务配置
     */
    val mcpConfig: Map<String, Any>? = null
) {
    /**
     * 转换为 Map 以便传递给 API
     * 注意：这里会将驼峰命名转换为下划线命名以匹配 SDK 要求
     */
    fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        
        systemPrompt?.let { map["systemPrompt"] = it }
        maxTurns?.let { map["maxTurns"] = it }
        cwd?.let { map["cwd"] = it }
        allowedTools?.let { map["allowedTools"] = it }
        permissionMode?.let { map["permissionMode"] = it }
        maxThinkingTokens?.let { map["maxThinkingTokens"] = it }
        model?.let { map["model"] = it }
        mcpConfig?.let { map["mcpServers"] = it }
        
        return map
    }
}