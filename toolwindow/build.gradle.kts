plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `java-library`  // 添加 java-library 插件以支持 api 配置
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    idea
}

group = "com.claudecodeplus"
version = "1.0-SNAPSHOT"

dependencies {
    // 依赖 cli-wrapper 模块
    implementation(project(":cli-wrapper")) {
        // 在插件环境下排除协程库
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-swing")
    }
    
    // Compose Desktop - 使用 compileOnly 避免与 IntelliJ 平台冲突
    compileOnly(compose.desktop.currentOs)
    compileOnly(compose.runtime)
    compileOnly(compose.foundation)
    compileOnly(compose.animation)
    compileOnly(compose.ui)
    compileOnly(compose.material) // 需要 Material 组件用于 DropdownMenu
    
    // Jewel UI - 使用 api 传递依赖
    val jewelVersion = rootProject.extra["jewelVersion"] as String
    
    // 从 Maven Central 引入 Jewel
    api("org.jetbrains.jewel:jewel-foundation:$jewelVersion")
    api("org.jetbrains.jewel:jewel-ui:$jewelVersion")
    api("org.jetbrains.jewel:jewel-int-ui-standalone:$jewelVersion")

    // 协程 - 使用 compileOnly 避免与 IntelliJ 平台冲突
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:${rootProject.extra["coroutinesVersion"]}")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-swing:${rootProject.extra["coroutinesVersion"]}")
    
    // Markdown 解析
    implementation("org.commonmark:commonmark:0.25.0")
    implementation("org.commonmark:commonmark-ext-gfm-tables:0.25.0")
    
    // Kotlinx Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    
    // Gson for JSON parsing (暂时保留，逐步迁移)
    implementation("com.google.code.gson:gson:2.10.1")
    
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
    testImplementation(compose.desktop.currentOs)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${rootProject.extra["coroutinesVersion"]}")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:${rootProject.extra["coroutinesVersion"]}")
}

compose.desktop {
    application {
        mainClass = "MainKt"
    }
}

// IDE configuration for source/javadoc download is handled by IDE settings