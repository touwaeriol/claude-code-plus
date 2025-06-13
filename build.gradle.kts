import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.22"
    id("org.jetbrains.intellij") version "1.17.2"
    id("org.jetbrains.compose") version "1.6.0"
    kotlin("plugin.serialization") version "1.9.22"
}

group = "com.claudecodeplus"
version = "1.0-SNAPSHOT"

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    // Compose for Desktop
    implementation(compose.desktop.currentOs)
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.ui)
    implementation(compose.components.resources)
    implementation(compose.components.uiToolingPreview)
    
    // Markdown 渲染
    implementation("org.jetbrains:markdown:0.5.2")
    
    // 协程支持
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.0")
    
    // JSON 处理
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    
    // 测试依赖
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
}

// IntelliJ 平台配置
intellij {
    version.set("2023.3.3")
    type.set("IC") // IntelliJ IDEA Community Edition
    
    plugins.set(listOf(
        "com.intellij.java",
        "org.jetbrains.kotlin"
    ))
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    
    withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = "17"
            freeCompilerArgs = listOf(
                "-opt-in=kotlin.RequiresOptIn",
                "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
                "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
                "-opt-in=org.jetbrains.compose.resources.ExperimentalResourceApi"
            )
        }
    }
    
    patchPluginXml {
        sinceBuild.set("233")
        untilBuild.set("241.*")
        
        pluginDescription.set("""
            Claude Code Plus - An enhanced UI for Claude Code CLI
            
            Features:
            - JetBrains AI-like chat interface
            - Smart file reference with @ completion
            - Automatic path resolution
            - Markdown rendering
            - Code highlighting
        """.trimIndent())
        
        changeNotes.set("""
            <b>1.0.0</b>
            <ul>
                <li>Initial release</li>
                <li>Basic chat interface with Claude Code</li>
                <li>File reference completion</li>
                <li>Markdown support</li>
            </ul>
        """.trimIndent())
    }
    
    buildSearchableOptions {
        enabled = false
    }
    
    runIde {
        // 增加内存
        jvmArgs("-Xmx2048m")
    }
    
    test {
        useJUnitPlatform()
    }
}
