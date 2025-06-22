package com.claudecodeplus.test

import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * 文件索引构建工具
 * 可以作为独立程序运行，预先构建项目索引
 */
object FileIndexBuilder {
    
    @JvmStatic
    fun main(args: Array<String>) {
        val projectPath = if (args.isNotEmpty()) {
            args[0]
        } else {
            System.getProperty("user.dir")
        }
        
        println("=== File Index Builder ===")
        println("Project path: $projectPath")
        println("Starting index build...")
        
        val index = CustomFileIndex(projectPath)
        
        // 等待索引构建完成
        runBlocking {
            var attempts = 0
            while (!index.isIndexReady() && attempts < 60) { // 最多等待60秒
                Thread.sleep(1000)
                attempts++
                print(".")
            }
            println()
            
            if (index.isIndexReady()) {
                println("Index build completed!")
                
                // 测试搜索功能
                println("\nTesting search functionality...")
                
                // 测试1：搜索kotlin文件
                println("\n1. Searching for 'kt' files:")
                val ktFiles = index.searchByName("kt", 5)
                ktFiles.forEach { println("  - $it") }
                
                // 测试2：搜索特定文件
                println("\n2. Searching for 'ClaudeCodePlus':")
                val specificFiles = index.searchByName("ClaudeCodePlus", 5)
                specificFiles.forEach { println("  - $it") }
                
                // 测试3：获取最近文件
                println("\n3. Recent files:")
                val recentFiles = index.getRecentFiles(5)
                recentFiles.forEach { println("  - $it") }
                
            } else {
                println("Index build failed or timed out!")
            }
        }
    }
    
    /**
     * 构建并返回索引
     */
    fun buildIndex(projectPath: String): CustomFileIndex {
        val index = CustomFileIndex(projectPath)
        
        // 等待索引就绪
        runBlocking {
            while (!index.isIndexReady()) {
                Thread.sleep(100)
            }
        }
        
        return index
    }
}