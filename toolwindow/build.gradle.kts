plugins {
    id("org.jetbrains.intellij.platform.module")
    kotlin("jvm")
    kotlin("plugin.serialization")
    kotlin("plugin.compose")























}

group = "com.claudecodeplus"
version = "1.0-SNAPSHOT"


dependencies {
    // 🎯 CRITICAL: The submodule MUST explicitly declare the IntelliJ Platform dependency
    // even though it's also declared in the main plugin module. This is required by
    // the org.jetbrains.intellij.platform.module plugin to generate the manifest correctly.
    intellijPlatform {
        // 🎯 Same version as the main plugin module - MUST match exactly
        create(providers.gradleProperty("platformType"), providers.gradleProperty("platformVersion"))

        // 🔧 When using multi-OS archives, we need to explicitly add JBR and tools
        jetbrainsRuntime()

        // 🎯 使用 IDE 平台内置的 Jewel 和 Compose 模块
        bundledModules(
            "intellij.libraries.skiko",
            "intellij.libraries.compose.foundation.desktop",
            "intellij.platform.jewel.foundation",
            "intellij.platform.jewel.ui",
            "intellij.platform.jewel.ideLafBridge",
            "intellij.platform.compose"
        )
    }

    // 依赖 claude-code-sdk 模块
    implementation(project(":claude-code-sdk"))

    // 依赖 claude-code-rpc-api 模块 (SDK 依赖此模块的类型)
    implementation(project(":claude-code-rpc-api"))

    // Kotlin 序列化运行时
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")






    // Markdown 解析 - 完整的CommonMark生态（保留用于其他功能）
    implementation("org.commonmark:commonmark:0.25.0")
    implementation("org.commonmark:commonmark-ext-gfm-tables:0.25.0")
    implementation("org.commonmark:commonmark-ext-gfm-strikethrough:0.25.0")
    implementation("org.commonmark:commonmark-ext-autolink:0.25.0")
    implementation("org.commonmark:commonmark-ext-task-list-items:0.25.0")
    implementation("org.commonmark:commonmark-ext-heading-anchor:0.25.0")

    // 注释掉不兼容的库 - 与 IntelliJ 平台的 Compose 集成冲突
    // implementation("com.mohamedrejeb.richeditor:richeditor-compose:1.0.0-rc11")

    // Kotlinx Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    // Gson已完全迁移到Kotlinx Serialization

    // Kotlin reflection
    implementation("org.jetbrains.kotlin:kotlin-reflect:${rootProject.extra["kotlinVersion"]}")

    // Mordant for ANSI text parsing and rendering
    implementation("com.github.ajalt.mordant:mordant:2.2.0")

    // Hutool for file watching
    implementation("cn.hutool:hutool-core:5.8.27")

    // Kotlin Logging
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    implementation("org.slf4j:slf4j-api:2.0.9")

    // 测试依赖
    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("junit:junit:4.13.2")
    // 🚫 移除外部Compose和协程依赖 - 使用IDE平台内置版本

}


// 🚫 移除Compose Multiplatform相关配置 - 使用IDE平台内置版本

// IDE configuration for source/javadoc download is handled by IDE settings


