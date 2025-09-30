package com.claudecodeplus.sdk.examples

import com.claudecodeplus.sdk.query
import com.claudecodeplus.sdk.types.*
import kotlinx.coroutines.runBlocking

/**
 * Example demonstrating programmatic subagents.
 *
 * New in v0.1.0: Agents can now be defined inline in code,
 * enabling dynamic agent creation without filesystem dependencies.
 */
fun main() = runBlocking {
    println("=== Programmatic Subagents Example ===\n")

    // Define custom agents inline
    val agents = mapOf(
        "code-reviewer" to AgentDefinition(
            description = "Reviews code for quality and best practices",
            prompt = """You are an expert code reviewer. Analyze code for:
                - Code quality and readability
                - Potential bugs or issues
                - Performance considerations
                - Best practices

                Provide constructive feedback.""".trimIndent(),
            tools = listOf("Read", "Grep"),
            model = "sonnet"
        ),

        "test-writer" to AgentDefinition(
            description = "Writes comprehensive unit tests",
            prompt = """You are a test automation expert. Write:
                - Comprehensive unit tests
                - Edge case coverage
                - Clear test descriptions
                - Mock/stub setup when needed""".trimIndent(),
            tools = listOf("Read", "Write", "Bash"),
            model = "sonnet"
        ),

        "documentation-expert" to AgentDefinition(
            description = "Creates clear documentation",
            prompt = """You are a technical writer. Create:
                - Clear API documentation
                - Usage examples
                - Architecture explanations
                - Code comments when appropriate""".trimIndent(),
            tools = listOf("Read", "Write", "Grep"),
            model = "haiku" // Faster model for simpler task
        )
    )

    val options = ClaudeAgentOptions(
        agents = agents,
        allowedTools = listOf("Read", "Write", "Grep", "Bash", "Task"),
        permissionMode = PermissionMode.DEFAULT
    )

    // Now you can use /agent command to invoke these agents
    println("Defined agents:")
    agents.forEach { (name, agent) ->
        println("  - /$name: ${agent.description}")
    }

    println("\nYou can now use these agents like:")
    println("  'Review this code file' → uses code-reviewer agent")
    println("  'Write tests for this function' → uses test-writer agent")
    println("  'Document this API' → uses documentation-expert agent")

    // Example query using agents
    println("\nExample: Using code-reviewer agent")
    query(
        prompt = "/agent code-reviewer Review the main function in src/Example.kt",
        options = options
    ).collect { message ->
        when (message) {
            is AssistantMessage -> {
                message.content.forEach { block ->
                    when (block) {
                        is TextBlock -> println(block.text)
                        is ToolUseBlock -> println("[Using tool: ${block.name}]")
                        else -> {}
                    }
                }
            }
            is ResultMessage -> {
                println("\nAgent task completed!")
                println("  Turns: ${message.numTurns}")
                println("  Duration: ${message.durationMs}ms")
                println("  Cost: $${message.totalCostUsd ?: 0.0}")
            }
            else -> {}
        }
    }

    println("\n=== End of Agents Example ===")
}