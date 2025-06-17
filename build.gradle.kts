import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.6.0"
    kotlin("plugin.serialization") version "2.1.0"
    id("org.jetbrains.compose") version "1.8.0-alpha04"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0"
    application
}

group = "com.claudecodeplus"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    google()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    
    // IntelliJ Platform Gradle Plugin Repositories Extension
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    // IntelliJ Platform dependencies
    intellijPlatform {
        intellijIdeaCommunity("2025.1.2")
        
        bundledPlugins("com.intellij.java", "org.intellij.plugins.markdown")
        
        // 测试所需的 IntelliJ Platform 依赖
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }
    
    // 使用 IntelliJ Platform 的 Kotlin 标准库
    compileOnly(kotlin("stdlib"))
    
    // Compose Desktop
    implementation(compose.desktop.currentOs)
    implementation(compose.ui)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.runtime)
    
    // 暂时移除 Jewel，使用基础 Compose
    // TODO: 后续添加 Jewel UI 框架支持
    
    // CommonMark - 与 IntelliJ 保持一致的版本
    implementation("org.commonmark:commonmark:0.24.0")
    implementation("org.commonmark:commonmark-ext-autolink:0.24.0")
    implementation("org.commonmark:commonmark-ext-gfm-strikethrough:0.24.0")
    implementation("org.commonmark:commonmark-ext-gfm-tables:0.24.0")
    
    // JSON 处理
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    implementation("com.google.code.gson:gson:2.10.1")
    
    // ANSI 解析
    implementation("org.fusesource.jansi:jansi:2.4.1")
    
    // HTTP客户端
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")
    
    // PTY 支持 - 移除，使用 IntelliJ Platform 内置的
    // IntelliJ Platform 已经包含了 pty4j 和 JNA
    
    // GraalVM Polyglot API
    implementation("org.graalvm.polyglot:polyglot:24.1.1")
    implementation("org.graalvm.polyglot:python:24.1.1")
    
    // Markdown 支持由 IntelliJ 平台的 Markdown 插件提供
    
    // 测试依赖
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    
    // IntelliJ Platform 测试框架
    testImplementation("com.intellij.remoterobot:remote-robot:0.11.16")
    testImplementation("com.intellij.remoterobot:remote-fixtures:0.11.16")
}

// IntelliJ 平台配置
intellijPlatform {
    pluginConfiguration {
        name = "Claude Code Plus"
        version = "1.0-SNAPSHOT"
        
        ideaVersion {
            sinceBuild = "243"
            untilBuild = "252.*"  // 支持到 2025.2
        }
        
        description = """
            Claude Code Plus - An enhanced UI for Claude Code CLI
            
            Features:
            - JetBrains AI-like chat interface
            - Smart file reference with @ completion
            - Automatic path resolution
            - Markdown rendering
            - Code highlighting
        """.trimIndent()
        
        changeNotes = """
            <b>1.0.0</b>
            <ul>
                <li>Initial release</li>
                <li>Basic chat interface with Claude Code</li>
                <li>File reference completion</li>
                <li>Markdown support</li>
                <li>Full K2 mode compatibility</li>
            </ul>
        """.trimIndent()
    }
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
    
    withType<KotlinCompile> {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
            freeCompilerArgs.addAll(
                "-opt-in=kotlin.RequiresOptIn",
                "-Xjvm-default=all",
                "-Xcontext-receivers"
            )
            // 使用 Kotlin 2.1 支持 K2 mode
            languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_1)
            apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_1)
        }
    }
    
    // 确保使用 IntelliJ Platform 的 Kotlin 标准库
    runIde {
        jvmArgs(
            "-Xmx2048m", 
            "-XX:+UseG1GC", 
            "-XX:+UseStringDeduplication",
            // GraalVM 相关参数
            "-Dpolyglot.python.ForceImportSite=true",
            "-Dpolyglot.engine.WarnInterpreterOnly=false"
        )
        
        // 如果使用 GraalVM，设置环境变量
        if (System.getProperty("java.vendor")?.contains("GraalVM") == true) {
            environment("GRAALVM_HOME", System.getProperty("java.home"))
        }
        
        // 在 Apple Silicon Mac 上使用原生架构

        systemProperty("idea.platform.arch", "aarch64")

    }
    
    
    buildSearchableOptions {
        enabled = false
    }
    
    test {
        useJUnitPlatform()
    }
    
    // 创建一个任务来运行简单测试
    register<JavaExec>("runSimpleTest") {
        group = "verification"
        description = "Run simple SDK test"
        classpath = sourceSets["test"].runtimeClasspath
        mainClass.set("com.claudecodeplus.core.SimpleSDKTestKt")
    }
    
    // 运行独立的 GraalVM 测试
    register<JavaExec>("runStandaloneTest") {
        group = "verification"
        description = "Run standalone GraalVM test"
        classpath = sourceSets["main"].runtimeClasspath + sourceSets["test"].runtimeClasspath
        mainClass.set("com.claudecodeplus.core.StandaloneSDKTestKt")
    }
    
    // 运行 Mock 测试
    register<JavaExec>("runMockTest") {
        group = "verification"
        description = "Run mocked SDK test"
        classpath = sourceSets["test"].runtimeClasspath
        mainClass.set("com.claudecodeplus.core.MockedSDKTestKt")
    }
    
    // 运行基础测试
    register<JavaExec>("runBasicTest") {
        group = "verification"
        description = "Run basic SDK test"
        classpath = sourceSets["test"].runtimeClasspath
        mainClass.set("com.claudecodeplus.core.BasicSDKTestKt")
        
        // 添加系统属性以禁用某些 IntelliJ 检查
        systemProperty("idea.force.use.core.classloader", "true")
        systemProperty("idea.use.core.classloader.for", "ALL")
        
        // 设置类加载器
        jvmArgs("-Djava.system.class.loader=com.intellij.util.lang.PathClassLoader")
    }
    // 修复重复文件问题
    distTar {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
    
    distZip {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
}

application {
    mainClass.set("com.claudecodeplus.ClaudeCodePlus")
}

// PTY 测试任务
tasks.register<JavaExec>("testPty") {
    group = "application"
    mainClass.set("com.claudecodeplus.core.TestPtySessionKt")
    classpath = sourceSets["main"].runtimeClasspath
}
