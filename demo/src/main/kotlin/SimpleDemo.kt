/*
 * SimpleDemo.kt
 * 
 * 简化版的内联文件引用功能验证 - 纯逻辑验证，无UI依赖
 */

/**
 * 内联引用数据类
 */
data class InlineReference(
    val displayText: String,    // @文件名
    val markdownText: String,   // [@文件名](file://path)
    val startIndex: Int,
    val endIndex: Int
)

/**
 * 解析 Markdown 格式的文件引用
 * 输入: "这是一个 [@架构设计.md](file:///path/to/file.md) 文件"
 * 输出: 显示文本 "这是一个 @架构设计.md 文件" + 引用列表
 */
fun parseMarkdownReferences(text: String): Pair<String, List<InlineReference>> {
    val pattern = Regex("""(\[@([^\]]+)\]\(file://([^)]+)\))""")
    val references = mutableListOf<InlineReference>()
    var processedText = text
    var offset = 0
    
    println("=== 解析输入文本 ===")
    println("原始文本: $text")
    
    // 从后往前处理，避免索引偏移问题
    pattern.findAll(text).toList().reversed().forEach { match ->
        val fullMatch = match.groupValues[1]  // [@文件名](file://path)
        val fileName = match.groupValues[2]   // 文件名
        val filePath = match.groupValues[3]   // 文件路径
        val displayText = "@$fileName"        // @文件名
        
        println("找到引用: $fullMatch -> 显示为: $displayText")
        
        val reference = InlineReference(
            displayText = displayText,
            markdownText = fullMatch,
            startIndex = match.range.first - offset,
            endIndex = match.range.first - offset + displayText.length
        )
        
        references.add(0, reference)
        
        // 替换为显示文本
        processedText = processedText.replaceRange(
            match.range.first - offset,
            match.range.last + 1 - offset,
            displayText
        )
        
        // 更新偏移量
        offset += fullMatch.length - displayText.length
    }
    
    println("处理后文本: $processedText")
    println("引用数量: ${references.size}")
    println()
    
    return processedText to references
}

/**
 * 模拟@符号文件选择
 */
fun simulateFileSelection(fileName: String, filePath: String): String {
    return "[@$fileName](file://$filePath)"
}

/**
 * 测试完整流程
 */
fun testCompleteFlow() {
    println("🔧 内联文件引用功能验证")
    println("=" * 50)
    
    // 测试1：单个文件引用
    println("📝 测试1: 单个文件引用")
    val docsDir = "${System.getProperty("user.home")}/codes/docs"
    val singleRef = "请查看 [@架构设计.md](file://$docsDir/架构设计.md) 了解详情"
    val (displayText1, refs1) = parseMarkdownReferences(singleRef)

    // 测试2：多个文件引用
    println("📝 测试2: 多个文件引用")
    val multiRef = "参考 [@架构设计.md](file://$docsDir/架构设计.md) 和 [@功能特性.md](file://$docsDir/功能特性.md) 文档"
    val (displayText2, refs2) = parseMarkdownReferences(multiRef)

    // 测试3：模拟用户输入流程
    println("📝 测试3: 模拟用户输入流程")
    val userInput = "我需要了解"
    val selectedFile = simulateFileSelection("部署指南.md", "$docsDir/部署指南.md")
    val combinedText = "$userInput $selectedFile 的内容"
    
    println("用户输入: $userInput")
    println("选择文件后生成: $selectedFile")
    println("组合文本: $combinedText")
    
    val (finalDisplayText, finalRefs) = parseMarkdownReferences(combinedText)
    
    // 测试结果验证
    println("📊 验证结果")
    println("=" * 30)
    println("✓ 输入框应显示: $finalDisplayText")
    println("✓ 发送给AI的格式: $combinedText")
    println("✓ 包含 ${finalRefs.size} 个文件引用")
    
    finalRefs.forEach { ref ->
        println("  - 显示: ${ref.displayText}")
        println("    存储: ${ref.markdownText}")
    }
    
    println("\n🎯 关键功能验证:")
    println("1. Markdown解析: ${if (refs1.isNotEmpty()) "✅ 通过" else "❌ 失败"}")
    println("2. 多引用处理: ${if (refs2.size == 2) "✅ 通过" else "❌ 失败"}")
    println("3. 文本替换: ${if (displayText1.contains("@架构设计.md") && !displayText1.contains("[@")) "✅ 通过" else "❌ 失败"}")
    println("4. 引用生成: ${if (selectedFile.startsWith("[@") && selectedFile.endsWith(")"))"✅ 通过" else "❌ 失败"}")
}

/**
 * 主函数
 */
fun main() {
    testCompleteFlow()
}