package com.asakii.plugin.agents

import com.asakii.claude.agent.sdk.types.AgentDefinition
import com.asakii.plugin.utils.ResourceLoader
import com.asakii.server.agents.AgentDefinitionsProvider
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * JetBrains 平台的子代理定义提供者实现
 *
 * 从 resources/agents 目录加载子代理定义文件。
 * 这些子代理利用 JetBrains IDE 的索引和分析能力。
 */
class AgentDefinitionsProviderImpl : AgentDefinitionsProvider {

    private val cachedDefinitions: Map<String, AgentDefinition> by lazy {
        loadDefinitions()
    }

    override fun getAgentDefinitions(): Map<String, AgentDefinition> {
        return cachedDefinitions
    }

    private fun loadDefinitions(): Map<String, AgentDefinition> {
        return try {
            val agents = ResourceLoader.loadAllAgentDefinitions()
            if (agents.isNotEmpty()) {
                logger.info { "📦 加载了 ${agents.size} 个子代理: ${agents.keys.joinToString()}" }
            }
            agents
        } catch (e: Exception) {
            logger.error(e) { "❌ 加载子代理定义失败" }
            emptyMap()
        }
    }
}
