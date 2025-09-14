plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `java-library`
    id("org.jetbrains.intellij.platform")
    // 🎯 关键：需要Compose插件来处理内建Compose库的编译
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    idea
}

group = "com.claudecodeplus"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    
    // IntelliJ Platform Gradle Plugin Repositories Extension
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    // 依赖 cli-wrapper 模块
    implementation(project(":cli-wrapper")) {
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-swing")
    }
    
    // 🎯 完全使用IDE内建依赖 - 正确的配置方式
    intellijPlatform {
        intellijIdeaCommunity("2025.1.4.1")
        
        // Jewel UI库 - IDE内建版本
        bundledModule("intellij.platform.jewel.foundation")
        bundledModule("intellij.platform.jewel.ui") 
        bundledModule("intellij.platform.jewel.ideLafBridge")
        
        // Compose库 - IDE内建版本（使用确定存在的模块）
        bundledModule("intellij.libraries.compose.foundation.desktop")
        bundledModule("intellij.libraries.skiko")
    }
    
    // Markdown 解析
    implementation("org.commonmark:commonmark:0.25.0")
    implementation("org.commonmark:commonmark-ext-gfm-tables:0.25.0")
    
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
    // 🚫 移除外部Compose和协程依赖 - 使用IDE平台内置版本
}

// 🚫 移除Compose Multiplatform相关配置 - 使用IDE平台内置版本

// IDE configuration for source/javadoc download is handled by IDE settings