package com.asakii.server.agents

import com.asakii.claude.agent.sdk.types.AgentDefinition

/**
 * 子代理定义提供者接口
 *
 * 由于子代理定义需要与特定平台集成（如 JetBrains IDE），
 * 实现类应在对应的平台模块中创建。
 * 此接口用于解耦 ai-agent-server 和平台模块的依赖。
 */
interface AgentDefinitionsProvider {
    /**
     * 获取所有子代理定义
     *
     * @return 代理名称到定义的映射
     */
    fun getAgentDefinitions(): Map<String, AgentDefinition>
}

/**
 * 默认实现（不提供任何子代理）
 */
object DefaultAgentDefinitionsProvider : AgentDefinitionsProvider {
    override fun getAgentDefinitions(): Map<String, AgentDefinition> = emptyMap()
}
