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
    implementation("cn.hutool:hutool-core:5.8.27") // 文件监听
    // Gson已迁移到Kotlinx Serialization
    
    // 高性能缓存
    implementation("com.github.ben-manes.caffeine:caffeine:${rootProject.extra["caffeineVersion"]}")
    
    // ANSI 处理
    implementation("org.fusesource.jansi:jansi:2.4.1")
    
    // Kotlin 日志框架
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("ch.qos.logback:logback-classic:1.4.14")
    
    // 测试依赖
    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${rootProject.extra["coroutinesVersion"]}")
}

// 添加运行测试的任务
tasks.register<JavaExec>("runSerializationTest") {
    group = "verification"
    description = "Run Claude serialization system test"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.claudecodeplus.sdk.serialization.TestMainKt")
}

// 确保 Node.js 脚本和配置文件被包含在资源中
tasks.named<Jar>("jar") {
    from(".") {
        include("claude-sdk-wrapper.js")
        include("package.json")
        include("README.md")
        // 注意：不打包 node_modules，在运行时动态安装
        exclude("node_modules/**")
        exclude("**/*.log")
        exclude("test-sdk.js")
        into("nodejs")
    }
}

// 创建分发包任务
tasks.register<Zip>("createDistribution") {
    group = "distribution"
    description = "Create distribution package with Node.js runtime"
    
    from(".") {
        include("claude-sdk-wrapper.js")
        include("package.json")
        include("README.md")
        into("cli-wrapper")
    }
    
    archiveFileName.set("claude-code-plus-nodejs-runtime.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    
    dependsOn("installNodeDependencies")
}

// 安装 Node.js 依赖的任务
tasks.register<Exec>("installNodeDependencies") {
    group = "build"
    description = "Install Node.js dependencies for Claude Code SDK"
    commandLine("npm", "install", "--production")
    workingDir = file(".")
    
    inputs.file("package.json")
    outputs.dir("node_modules")
    
    // 设置环境变量
    environment("NODE_ENV", "production")
}

// 确保构建前安装 Node.js 依赖
tasks.named("build") {
    dependsOn("installNodeDependencies")
}

// 测试 SDK 集成的任务
tasks.register<Exec>("testSdkIntegration") {
    group = "verification"
    description = "Test Claude Code SDK integration"
    commandLine("node", "test-sdk.js")
    workingDir = file(".")
    
    dependsOn("installNodeDependencies")
}