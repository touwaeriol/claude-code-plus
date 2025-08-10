package com.claudecodeplus.sdk.serialization

/**
 * 测试序列化系统的简单入口
 */
fun main() {
    println("=".repeat(50))
    println("开始测试 Claude 序列化系统")
    println("=".repeat(50))
    println()
    
    // 1. 基础序列化测试
    println("1️⃣  运行基础序列化测试...")
    println("-".repeat(30))
    val basicResult = SerializationTest.runBasicTests()
    basicResult.printResults()
    
    println("\n" + "=".repeat(50))
    println()
    
    // 2. 消息转换器测试
    println("2️⃣  运行消息转换器测试...")
    println("-".repeat(30))
    val converterResult = MessageConverterTest.runConverterTests()
    converterResult.printResults()
    
    println("\n" + "=".repeat(50))
    
    // 3. 总体测试结果
    val totalTests = basicResult.totalTests + converterResult.totalTests
    val totalPassed = basicResult.passed + converterResult.passed
    val totalFailed = basicResult.failed + converterResult.failed
    val successRate = (totalPassed.toDouble() / totalTests * 100).toInt()
    
    println("📊 总体测试结果")
    println("-".repeat(30))
    println("总测试数: $totalTests")
    println("通过: $totalPassed")
    println("失败: $totalFailed")
    println("总成功率: $successRate%")
    
    if (totalFailed == 0) {
        println("\n🎉 所有测试全部通过！系统工作完美！")
    } else {
        println("\n⚠️  有测试失败，需要检查相关问题。")
    }
    
    println("=".repeat(50))
}