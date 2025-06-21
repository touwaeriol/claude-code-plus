import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.6.0"
}

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
    // IntelliJ Platform dependencies
    intellijPlatform {
        intellijIdeaCommunity("2025.1.2")
        
        // 添加 Markdown 插件依赖
        bundledPlugin("org.intellij.plugins.markdown")
    }
    
    // 使用 IntelliJ Platform 的 Kotlin 标准库
    compileOnly(kotlin("stdlib"))
    
    // JSON 处理
    implementation("org.json:json:20231013")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.3")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.3")
    
    // Kotlin 协程
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}

// IntelliJ 平台配置
intellijPlatform {
    pluginConfiguration {
        name = "Claude Code Plus"
        version = "1.0-SNAPSHOT"
        
        ideaVersion {
            sinceBuild = "243"
            untilBuild = "252.*"
        }
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
        }
    }
    
    
    runIde {
        jvmArgs(
            "-Xmx2048m"
        )
    }
    
    buildSearchableOptions {
        enabled = false
    }
    
    
    // 在打包插件之前，确保静态文件被包含
    register("preparePluginDistribution") {
        dependsOn("prepareSandbox")
        
        doLast {
            // 从沙箱复制文件到分发目录
            val sandboxPluginDir = file("${layout.buildDirectory.get()}/idea-sandbox/${intellijPlatform.sandboxContainer.get()}/plugins/claude-code-plus")
            val distDir = file("${layout.buildDirectory.get()}/distributions/temp-plugin")
            
            // 清理临时目录
            delete(distDir)
            
            // 复制整个插件目录
            copy {
                from(sandboxPluginDir)
                into(distDir.resolve("claude-code-plus"))
            }
            
            // 创建新的 ZIP 文件
            val zipFile = file("${layout.buildDirectory.get()}/distributions/claude-code-plus-1.0-SNAPSHOT-with-resources.zip")
            
            ant.withGroovyBuilder {
                "zip"("destfile" to zipFile) {
                    "fileset"("dir" to distDir)
                }
            }
            
            println("Created plugin distribution with resources: $zipFile")
        }
    }
    
}