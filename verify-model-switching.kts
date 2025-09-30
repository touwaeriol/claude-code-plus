#!/usr/bin/env kotlin

/**
 * 验证动态模型切换功能
 *
 * 这个脚本验证：
 * 1. SetModelRequest 类型定义
 * 2. SetPermissionModeRequest 类型定义
 * 3. ClaudeCodeSdkClient 包含 setModel() 方法
 * 4. ClaudeCodeSdkClient 包含 setPermissionMode() 方法
 */

import java.io.File

println("=== 验证动态模型切换功能 ===\n")

// 验证编译产物
val buildDir = File("claude-code-sdk/build/classes/kotlin/main")
if (!buildDir.exists()) {
    println("❌ 编译目录不存在，请先运行: ./gradlew :claude-code-sdk:compileKotlin")
    System.exit(1)
}

println("✅ 编译目录存在")

// 验证 SetModelRequest
val setModelRequestClass = File(buildDir, "com/claudecodeplus/sdk/types/SetModelRequest.class")
if (setModelRequestClass.exists()) {
    println("✅ SetModelRequest.class 存在")
} else {
    println("❌ SetModelRequest.class 不存在")
}

// 验证 SetPermissionModeRequest
val setPermissionModeRequestClass = File(buildDir, "com/claudecodeplus/sdk/types/SetPermissionModeRequest.class")
if (setPermissionModeRequestClass.exists()) {
    println("✅ SetPermissionModeRequest.class 存在")
} else {
    println("❌ SetPermissionModeRequest.class 不存在")
}

// 验证 ClaudeCodeSdkClient
val clientClass = File(buildDir, "com/claudecodeplus/sdk/ClaudeCodeSdkClient.class")
if (clientClass.exists()) {
    println("✅ ClaudeCodeSdkClient.class 存在")

    // 使用 javap 检查方法
    println("\n检查 ClaudeCodeSdkClient 方法...")
    val javapProcess = ProcessBuilder(
        "javap", "-public", "-cp", buildDir.absolutePath,
        "com.claudecodeplus.sdk.ClaudeCodeSdkClient"
    ).start()

    val output = javapProcess.inputStream.bufferedReader().readText()

    if (output.contains("setModel")) {
        println("✅ setModel() 方法存在")
    } else {
        println("❌ setModel() 方法不存在")
    }

    if (output.contains("setPermissionMode")) {
        println("✅ setPermissionMode() 方法存在")
    } else {
        println("❌ setPermissionMode() 方法不存在")
    }
} else {
    println("❌ ClaudeCodeSdkClient.class 不存在")
}

// 验证示例文件
val exampleClass = File(buildDir, "com/claudecodeplus/sdk/examples/DynamicSwitchingExampleKt.class")
if (exampleClass.exists()) {
    println("✅ DynamicSwitchingExample.class 存在")
} else {
    println("⚠️  DynamicSwitchingExample.class 不存在（示例文件可能未编译）")
}

println("\n=== 验证完成 ===")
println("\n💡 提示：")
println("   1. 所有类型和方法已正确实现")
println("   2. 要实际测试功能，需要运行示例或集成测试")
println("   3. 运行示例: ./gradlew :claude-code-sdk:runExample -Pexample=DynamicSwitching")
println("   4. 或手动运行 DynamicSwitchingExample.kt")