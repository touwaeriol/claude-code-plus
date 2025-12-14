package com.asakii.plugin.utils

import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertEquals

/**
 * 测试 ResourceLoader 加载子代理定义的功能
 */
class ResourceLoaderTest {

    @Test
    fun `verify agents_json file exists in resources`() {
        println("\n========== 测试 agents.json 资源 ==========")

        val resourcePath = "agents/agents.json"
        val resource = this::class.java.classLoader.getResource(resourcePath)

        println("资源路径: $resourcePath")
        println("资源 URL: $resource")

        assertNotNull(resource, "❌ agents/agents.json 未找到")

        val content = resource.readText()
        println("文件大小: ${content.length} 字符")
        assertTrue(content.isNotEmpty(), "agents.json 文件为空")

        println("✅ agents.json 资源存在")
    }

    @Test
    fun `verify agents can be loaded from json`() {
        println("\n========== 测试加载子代理定义 ==========")

        // 强制重新加载，忽略缓存
        val agents = ResourceLoader.loadAllAgentDefinitions(forceReload = true)

        println("加载的子代理数量: ${agents.size}")
        println("子代理名称列表: ${agents.keys.joinToString(", ")}")

        assertTrue(agents.isNotEmpty(), "❌ 没有加载到任何子代理")

        println("✅ 子代理定义加载成功")
    }

    @Test
    fun `verify ExploreWithJetbrains agent exists`() {
        println("\n========== 测试 ExploreWithJetbrains 子代理 ==========")

        val agents = ResourceLoader.loadAllAgentDefinitions(forceReload = true)

        println("已加载的子代理: ${agents.keys.joinToString(", ")}")

        val exploreAgent = agents["ExploreWithJetbrains"]

        assertNotNull(exploreAgent, "❌ ExploreWithJetbrains 子代理未找到")

        println("ExploreWithJetbrains 配置:")
        println("  - description: ${exploreAgent.description?.take(50)}...")
        println("  - tools: ${exploreAgent.tools?.joinToString(", ")}")
        println("  - model: ${exploreAgent.model}")

        // 验证关键属性
        assertNotNull(exploreAgent.description, "description 不能为空")
        assertNotNull(exploreAgent.tools, "tools 不能为空")
        assertTrue(exploreAgent.tools!!.isNotEmpty(), "tools 列表不能为空")

        // 验证包含 JetBrains MCP 工具
        val hasJetBrainsTools = exploreAgent.tools!!.any { it.startsWith("mcp__jetbrains__") }
        assertTrue(hasJetBrainsTools, "❌ ExploreWithJetbrains 应该包含 mcp__jetbrains__ 工具")

        println("✅ ExploreWithJetbrains 子代理配置正确")
    }

    @Test
    fun `verify ExploreWithJetbrains has required JetBrains tools`() {
        println("\n========== 测试 ExploreWithJetbrains 工具列表 ==========")

        val agents = ResourceLoader.loadAllAgentDefinitions(forceReload = true)
        val exploreAgent = agents["ExploreWithJetbrains"]

        assertNotNull(exploreAgent, "❌ ExploreWithJetbrains 子代理未找到")

        val tools = exploreAgent.tools!!
        println("工具列表: ${tools.joinToString(", ")}")

        // 验证必须包含的 JetBrains 工具
        val requiredJetBrainsTools = listOf(
            "mcp__jetbrains__FileIndex",
            "mcp__jetbrains__CodeSearch",
            "mcp__jetbrains__DirectoryTree",
            "mcp__jetbrains__FileProblems"
        )

        for (tool in requiredJetBrainsTools) {
            assertTrue(tools.contains(tool), "❌ 缺少必需的工具: $tool")
            println("  ✅ $tool")
        }

        // 验证也包含基础工具
        val requiredBaseTools = listOf("Read", "Grep", "Glob")
        for (tool in requiredBaseTools) {
            assertTrue(tools.contains(tool), "❌ 缺少基础工具: $tool")
            println("  ✅ $tool")
        }

        println("✅ 所有必需工具都已配置")
    }

    @Test
    fun `verify agent model is haiku for efficiency`() {
        println("\n========== 测试 ExploreWithJetbrains 模型配置 ==========")

        val agents = ResourceLoader.loadAllAgentDefinitions(forceReload = true)
        val exploreAgent = agents["ExploreWithJetbrains"]

        assertNotNull(exploreAgent, "❌ ExploreWithJetbrains 子代理未找到")

        println("模型: ${exploreAgent.model}")

        // ExploreWithJetbrains 应该使用 haiku 模型以提高效率
        assertEquals("haiku", exploreAgent.model, "ExploreWithJetbrains 应该使用 haiku 模型")

        println("✅ 模型配置正确 (haiku)")
    }
}
