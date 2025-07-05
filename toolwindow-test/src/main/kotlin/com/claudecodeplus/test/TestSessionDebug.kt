package com.claudecodeplus.test

import com.claudecodeplus.session.ClaudeSessionManager
import kotlinx.coroutines.runBlocking
import java.io.File

fun main() {
    println("=== Claude Session Manager Debug Test ===")
    
    val projectPath = "/Users/erio/codes/idea/claude-code-plus"
    val sessionManager = ClaudeSessionManager()
    
    // 测试路径编码
    val encodedPath = "-" + projectPath.replace("/", "-")
    println("Project Path: $projectPath")
    println("Encoded Path: $encodedPath")
    
    val homeDir = System.getProperty("user.home")
    val sessionDir = File(homeDir, ".claude/projects/$encodedPath")
    println("Session Directory: ${sessionDir.absolutePath}")
    println("Directory exists: ${sessionDir.exists()}")
    
    if (sessionDir.exists()) {
        val files = sessionDir.listFiles()?.filter { it.extension == "jsonl" } ?: emptyList()
        println("Found ${files.size} session files")
        
        files.take(5).forEach { file ->
            println("\nFile: ${file.name}")
            println("Size: ${file.length()} bytes")
            
            // 读取第一行
            val firstLine = file.bufferedReader().use { it.readLine() }
            println("First line (truncated): ${firstLine?.take(200)}...")
        }
    }
    
    // 测试 SessionManager 功能
    println("\n=== Testing SessionManager ===")
    
    runBlocking {
        try {
            val sessions = sessionManager.getSessionList(projectPath)
            println("SessionManager found ${sessions.size} sessions")
            
            sessions.take(3).forEach { session ->
                println("\nSession ID: ${session.sessionId}")
                println("Message Count: ${session.messageCount}")
                println("Last Message: ${session.lastMessage}")
                
                // 尝试读取消息
                val (messages, total) = sessionManager.readSessionMessages(
                    sessionId = session.sessionId,
                    projectPath = projectPath,
                    pageSize = 5,
                    page = 0
                )
                println("Read ${messages.size} messages (total: $total)")
            }
        } catch (e: Exception) {
            println("Error: ${e.message}")
            e.printStackTrace()
        }
    }
    
    println("\n=== Test Complete ===")
}