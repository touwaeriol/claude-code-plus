plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.claudecodeplus"
version = "1.0-SNAPSHOT"

dependencies {
    // 协程 - 使用 compileOnly，在插件环境由平台提供
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:${rootProject.extra["coroutinesVersion"]}")
    
    // JSON 处理
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    implementation("org.json:json:20231013") // 保留用于简单的 JSON 操作
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.3") // 暂时保留，稍后重构
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.3")
    
    // 文件监听和IO优化
    implementation("commons-io:commons-io:${rootProject.extra["commonsIoVersion"]}")
    
    // 高性能缓存
    implementation("com.github.ben-manes.caffeine:caffeine:${rootProject.extra["caffeineVersion"]}")
    
    // ANSI 处理
    implementation("org.fusesource.jansi:jansi:2.4.1")
    
    // 测试依赖
    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${rootProject.extra["coroutinesVersion"]}")
}