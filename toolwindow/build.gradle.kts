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
    implementation(project(":cli-wrapper"))
    
    // Compose Desktop - 使用 api 传递依赖
    api(compose.desktop.currentOs)
    api(compose.runtime)
    api(compose.foundation)
    api(compose.animation)
    api(compose.ui)
    api(compose.material) // 需要 Material 组件用于 DropdownMenu
    
    // Jewel UI - 使用 api 传递依赖
    val jewelVersion = rootProject.extra["jewelVersion"] as String
    
    // 从 Maven Central 引入 Jewel
    api("org.jetbrains.jewel:jewel-foundation:$jewelVersion")
    api("org.jetbrains.jewel:jewel-ui:$jewelVersion")
    api("org.jetbrains.jewel:jewel-int-ui-standalone:$jewelVersion")

    // 协程
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:${rootProject.extra["coroutinesVersion"]}")
    
    // Markdown 解析
    implementation("org.commonmark:commonmark:0.25.0")
    
    // JSON 序列化
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    
    // 测试依赖
    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.13.8")
}

compose.desktop {
    application {
        mainClass = "MainKt"
    }
}

// IDE configuration for source/javadoc download is handled by IDE settings