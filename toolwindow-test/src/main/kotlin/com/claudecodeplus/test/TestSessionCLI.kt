package com.claudecodeplus.test

import com.claudecodeplus.session.ClaudeSessionManager
import kotlinx.coroutines.runBlocking

fun main() {
    println("=== Claude Session Manager CLI Test ===\n")
    
    val projectPath = "/Users/erio/codes/idea/claude-code-plus"
    val sessionManager = ClaudeSessionManager()
    
    runBlocking {
        println("Testing project: $projectPath")
        println("Loading sessions...\n")
        
        try {
            val sessions = sessionManager.getSessionList(projectPath)
            println("Found ${sessions.size} sessions\n")
            
            if (sessions.isEmpty()) {
                println("No sessions found. Check if the path is correct.")
            } else {
                // 显示前5个会话
                sessions.take(5).forEachIndexed { index, session ->
                    println("Session ${index + 1}:")
                    println("  ID: ${session.sessionId}")
                    println("  Messages: ${session.messageCount}")
                    println("  Modified: ${java.time.Instant.ofEpochMilli(session.lastModified)}")
                    println("  Last message: ${session.lastMessage?.take(80) ?: "N/A"}...")
                    println()
                }
                
                // 测试读取第一个会话的消息
                if (sessions.isNotEmpty()) {
                    println("\n=== Testing Message Loading ===")
                    val firstSession = sessions.first()
                    println("Loading messages for session: ${firstSession.sessionId}")
                    
                    val (messages, total) = sessionManager.readSessionMessages(
                        sessionId = firstSession.sessionId,
                        projectPath = projectPath,
                        pageSize = 10,
                        page = 0
                    )
                    
                    println("Loaded ${messages.size} messages (total: $total)")
                    
                    messages.take(3).forEachIndexed { index, msg ->
                        println("\nMessage ${index + 1}:")
                        println("  Type: ${msg.type}")
                        println("  Time: ${msg.timestamp}")
                        val content = when (val c = msg.message.content) {
                            is String -> c.take(100)
                            is List<*> -> "List content (${c.size} items)"
                            else -> "Unknown content type"
                        }
                        println("  Content: $content...")
                    }
                }
            }
        } catch (e: Exception) {
            println("Error: ${e.message}")
            e.printStackTrace()
        }
    }
    
    println("\n=== Test Complete ===")
}