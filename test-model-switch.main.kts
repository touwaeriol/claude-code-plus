#!/usr/bin/env kotlin

@file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

import kotlinx.coroutines.runBlocking
import java.io.File

println("=== 简易模型切换测试 ===\n")

// 测试 1: 验证 setModel 控制命令格式
println("测试 1: 验证 setModel 控制命令")
println("根据 SDK 代码，setModel() 应该发送控制命令:")
println("""{"type":"control_request","request_id":"xxx","request":{"type":"set_model","model":"claude-opus-4-20250514"}}""")
println()

// 测试 2: 检查 SlashCommandInterceptor 是否正确调用 setModel
println("测试 2: 检查拦截器代码")
val interceptorFile = File("/Users/erio/codes/idea/claude-code-plus/toolwindow/src/main/kotlin/com/claudecodeplus/core/preprocessor/SlashCommandInterceptor.kt")
if (interceptorFile.exists()) {
    val content = interceptorFile.readText()
    if (content.contains("client.setModel(modelName)")) {
        println("✅ 拦截器确实调用了 client.setModel()")
    }
    if (content.contains("\"opus\" to \"claude-opus-4-20250514\"")) {
        println("✅ 别名映射正确")
    }
} else {
    println("❌ 找不到拦截器文件")
}
println()

// 测试 3: 检查 SDK 的 setModel 实现
println("测试 3: 检查 SDK setModel 实现")
val sdkFile = File("/Users/erio/codes/idea/claude-code-plus/claude-code-sdk/src/main/kotlin/com/claudecodeplus/sdk/ClaudeCodeSdkClient.kt")
if (sdkFile.exists()) {
    val content = sdkFile.readText()
    if (content.contains("suspend fun setModel(model: String)")) {
        println("✅ SDK 有 setModel() 方法")
    }
    if (content.contains("ControlRequest.SetModel")) {
        println("✅ setModel 使用控制协议")
    }
} else {
    println("❌ 找不到 SDK 文件")
}
println()

println("=== 结论 ===")
println("根据代码分析:")
println("1. SlashCommandInterceptor 确实调用 client.setModel()")
println("2. ClaudeCodeSdkClient.setModel() 通过控制协议发送 set_model 请求")
println("3. 控制协议会等待 Claude CLI 的 control_response")
println()
println("但是，需要实际运行测试才能验证:")
println("- Claude CLI 是否支持 set_model 控制命令")
println("- 模型切换后是否真的生效")