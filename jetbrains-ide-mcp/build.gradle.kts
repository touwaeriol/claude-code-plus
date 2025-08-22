import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jetbrains.intellij.platform")
    application
}

// 使用父项目的版本配置
val kotlinVersion: String by rootProject.extra
val coroutinesVersion: String by rootProject.extra
val serializationVersion: String by rootProject.extra

group = "com.claudecodeplus"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    
    // IntelliJ Platform Gradle Plugin Repositories Extension
    intellijPlatform {
        defaultRepositories()
        marketplace()
    }
}

dependencies {
    // MCP Java SDK 核心依赖
    implementation("io.modelcontextprotocol.sdk:mcp:0.11.2")
    
    // IntelliJ Platform SDK
    intellijPlatform {
        intellijIdeaCommunity("2025.1.4.1")
    }
    
    // Kotlin 标准库和协程
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    
    // JSON 序列化
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")
    
    // HTTP 服务器支持 (使用轻量级的 Ktor)
    implementation("io.ktor:ktor-server-core:2.3.7")
    implementation("io.ktor:ktor-server-netty:2.3.7")
    implementation("io.ktor:ktor-server-content-negotiation:2.3.7")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.7")
    
    // 日志
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("ch.qos.logback:logback-classic:1.4.14")
    
    // 测试依赖
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
    testImplementation("io.ktor:ktor-server-test-host:2.3.7")
    testImplementation("io.ktor:ktor-client-cio:2.3.7")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.10.1")
}

// IntelliJ Platform 配置
intellijPlatform {
    buildSearchableOptions = false
    
    instrumentCode = false
}

// Kotlin 编译配置
tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.RequiresOptIn",
            "-Xjsr305=strict"
        )
    }
}

// Java 兼容性
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// 应用程序配置
application {
    mainClass.set("com.claudecodeplus.mcp.ParameterFormatDemo")
}

tasks.test {
    useJUnitPlatform()
}