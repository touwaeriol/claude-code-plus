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
    
    // 压缩文件处理
    implementation("org.apache.commons:commons-compress:1.25.0")
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

// 辅助函数：检查系统 Node.js 可用性
fun checkNodeAvailability(): Boolean {
    return try {
        val process = ProcessBuilder("node", "--version").start()
        val exitCode = process.waitFor()
        exitCode == 0
    } catch (e: Exception) {
        false
    }
}

// 辅助函数：获取 Node.js 版本
fun getNodeVersion(): String? {
    return try {
        val process = ProcessBuilder("node", "--version").start()
        if (process.waitFor() == 0) {
            process.inputStream.bufferedReader().readText().trim()
        } else null
    } catch (e: Exception) {
        null
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
    
    // 检查 Node.js 环境
    register("checkNodeEnvironment") {
        description = "Check Node.js environment availability"
        group = "verification"
        
        doLast {
            println("Checking Node.js environment...")
            
            if (checkNodeAvailability()) {
                val version = getNodeVersion()
                println("✅ Node.js found: $version")
                
                // 检查版本是否满足最低要求
                val versionNumber = version?.removePrefix("v")?.split(".")?.get(0)?.toIntOrNull()
                if (versionNumber != null && versionNumber >= 18) {
                    println("✅ Node.js version meets minimum requirement (>=18.0.0)")
                } else {
                    println("⚠️  Node.js version may be too old, recommend >=18.0.0 (Claude SDK 要求)")
                }
            } else {
                println("❌ Node.js not found in PATH")
                println("Please install Node.js 18.0.0 or higher")
                throw GradleException("Node.js is required but not found")
            }
        }
    }
    
    // 构建 Node.js 服务
    register("buildNodeService") {
        description = "Build Node.js service code for embedding"
        group = "build"
        dependsOn("checkNodeEnvironment")
        
        doLast {
            val wrapperDir = file("claude-sdk-wrapper")
            if (!wrapperDir.exists()) {
                throw GradleException("claude-sdk-wrapper directory not found")
            }
            
            println("Building Node.js service...")
            
            // 安装依赖
            exec {
                workingDir = wrapperDir
                commandLine = listOf("npm", "ci", "--omit=optional")
            }
            
            // 使用新的 esbuild 构建脚本编译并复制到 resources
            exec {
                workingDir = wrapperDir
                commandLine = listOf("npm", "run", "build:plugin:esbuild")
            }
            
            println("Node.js service built and copied to resources successfully!")
        }
    }
    
    // 清理 Node.js 资源
    register("cleanNodeResources") {
        description = "Clean Node.js resources"
        group = "build"
        
        doLast {
            val nodeDir = file("src/main/resources/claude-node")
            if (nodeDir.exists()) {
                nodeDir.deleteRecursively()
                println("Cleaned Node.js resources")
            }
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
    
    // 编译 Kotlin 代码时自动构建 Node 服务
    named("compileKotlin") {
        dependsOn("buildNodeService")
    }
    
    // 处理资源时确保 Node 服务已构建
    named<ProcessResources>("processResources") {
        dependsOn("buildNodeService")
    }
    
    // 准备沙箱时确保 Node 服务已构建
    named<org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask>("prepareSandbox") {
        dependsOn("buildNodeService")
        
        // 使用 IntelliJ Platform 的方式添加文件到插件目录
        from("src/main/resources/claude-node") {
            into("claude-node")
            exclude("**/*.ts", "**/*.map")  // 排除源文件
        }
        
        doLast {
            println("Node service files prepared in sandbox")
        }
    }
    
    // 构建插件时也构建 Node 服务
    named("buildPlugin") {
        dependsOn("buildNodeService")
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
    
    // 清理时也清理 Node.js 资源
    named("clean") {
        dependsOn("cleanNodeResources")
    }
}