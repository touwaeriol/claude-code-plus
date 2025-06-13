import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
    kotlin("jvm") version "2.0.21"
    id("org.jetbrains.intellij.platform") version "2.6.0"
    id("org.jetbrains.compose") version "1.7.3"
    kotlin("plugin.serialization") version "2.0.21"
    kotlin("plugin.compose") version "2.0.21"
    application
}

group = "com.claudecodeplus"
version = "1.0-SNAPSHOT"

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    
    // IntelliJ Platform Gradle Plugin Repositories Extension
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    // IntelliJ Platform dependencies
    intellijPlatform {
        intellijIdeaCommunity("2025.1")
        
        bundledPlugins("com.intellij.java", "org.jetbrains.kotlin")
    }
    
    // 使用 IntelliJ Platform 的 Kotlin 标准库
    compileOnly(kotlin("stdlib"))
    
    // Compose for Desktop - 不包含 coroutines
    implementation(compose.desktop.currentOs) {
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-swing")
    }
    implementation(compose.runtime) {
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
    }
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.ui)
    implementation(compose.components.resources)
    implementation(compose.components.uiToolingPreview)
    
    // Markdown 渲染
    implementation("org.jetbrains:markdown:0.5.2")
    
    // JSON 处理
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    
    // PTY 支持
    implementation("org.jetbrains.pty4j:pty4j:0.12.13")
    
    // 测试依赖
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

// IntelliJ 平台配置
intellijPlatform {
    pluginConfiguration {
        name = "Claude Code Plus"
        version = "1.0-SNAPSHOT"
        
        ideaVersion {
            sinceBuild = "251"
            untilBuild = "251.*"
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
            </ul>
        """.trimIndent()
    }
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    
    withType<KotlinCompile> {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            freeCompilerArgs.addAll(
                "-opt-in=kotlin.RequiresOptIn",
                "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
                "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
                "-opt-in=org.jetbrains.compose.resources.ExperimentalResourceApi",
                "-Xjvm-default=all"
            )
        }
    }
    
    // 确保使用 IntelliJ Platform 的 Kotlin 标准库
    runIde {
        jvmArgs("-Xmx2048m", "-XX:+UseG1GC", "-XX:+UseStringDeduplication")
        systemProperty("idea.kotlin.plugin.use.k2", "false")
    }
    
    
    buildSearchableOptions {
        enabled = false
    }
    
    test {
        useJUnitPlatform()
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
