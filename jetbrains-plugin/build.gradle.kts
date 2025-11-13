plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

group = "com.claudecodeplus"
version = "1.0.4"

repositories {
    mavenCentral()
    
    // IntelliJ Platform Gradle Plugin Repositories Extension
    intellijPlatform {
        defaultRepositories()
        marketplace()
    }
}

dependencies {
    // 依赖其他模块 - 按照官方方式排除整个 kotlinx 组
    implementation(project(":toolwindow")) {
        exclude(group = "org.jetbrains.kotlinx")
        // 🎯 现在toolwindow使用内建依赖，不需要复杂的排除规则
    }

    // 添加 claude-code-sdk 依赖
    implementation(project(":claude-code-sdk"))
    
    // 🎯 使用IDE平台内置的Jewel模块 - 替换外部依赖
    // 移除所有外部Jewel依赖，使用IDE内置版本
    
    // IntelliJ Platform dependencies
    intellijPlatform {
        // 使用 2025.1.4.1 版本（稳定支持 Compose）
        // 注意：虽然 IDE 是 2025.2.3，但插件SDK保持向后兼容
        intellijIdeaCommunity("2025.1.4.1")
        
        // 🎯 Jewel和Compose内置模块 - 官方推荐方式！
        bundledModule("intellij.platform.jewel.foundation")
        bundledModule("intellij.platform.jewel.ui")
        bundledModule("intellij.platform.jewel.ideLafBridge")
        bundledModule("intellij.libraries.compose.foundation.desktop")  // Compose Foundation
        bundledModule("intellij.libraries.skiko")  // Compose的原生渲染库
        
        // 添加 Markdown 插件依赖
        bundledPlugin("org.intellij.plugins.markdown")
        
        // 添加 Git 插件依赖
        bundledPlugin("Git4Idea")
        
        // 添加 Java 插件依赖（用于 PSI 类）
        bundledPlugin("com.intellij.java")
    }
    
    // 🔧 添加 Compose Runtime 依赖（编译时需要）
    compileOnly(compose.runtime)

    // 使用 IntelliJ Platform 的 Kotlin 标准库
    compileOnly(kotlin("stdlib"))
    
    // 🔧 编译时需要协程 API，但运行时会被排除，使用 IntelliJ Platform 内置版本
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${rootProject.extra["coroutinesVersion"]}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:${rootProject.extra["coroutinesVersion"]}")

    // 🔧 Kotlin serialization 运行时依赖
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${rootProject.extra["serializationVersion"]}")

    // Ktor 服务器依赖 - 使用 3.0.3 版本（支持 SSE 和 WebSocket）
    val ktorVersion = "3.0.3"
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-cio:$ktorVersion")
    implementation("io.ktor:ktor-server-sse:$ktorVersion")
    implementation("io.ktor:ktor-server-websockets:$ktorVersion")  // ✅ WebSocket 支持
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-cors:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

    // 测试依赖
    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.13.8")
}

// IntelliJ 平台配置
intellijPlatform {
    pluginConfiguration {
        name = "Claude Code Plus"
        version = project.version.toString()

        ideaVersion {
            sinceBuild = "243"
            untilBuild = "252.*"
        }

        // description 和 changeNotes 会从 plugin.xml 自动读取，无需手动配置
    }
    
    // 签名配置（需要证书）
    // 注意：首次发布可以不签名，后续建议添加签名
    // signing {
    //     certificateChainFile = file("certificate-chain.crt")
    //     privateKeyFile = file("private-key.pem") 
    //     password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    // }
    
    // 发布配置
    publishing {
        token = providers.environmentVariable("ORG_GRADLE_PROJECT_intellijPlatformPublishingToken")
            .orElse(providers.gradleProperty("intellijPlatformPublishingToken"))
        
        // 发布渠道：stable, beta, alpha, eap
        channels = listOf("stable")
    }
}

// ===== 前端构建任务 =====

// 获取 npm 命令（Windows 使用 npm.cmd）
val npmCommand = if (System.getProperty("os.name").lowercase().contains("windows")) {
    "npm.cmd"
} else {
    "npm"
}

// 检查 Node.js 是否安装
val checkNodeInstalled by tasks.registering(Exec::class) {
    group = "frontend"
    description = "Check if Node.js is installed"

    commandLine("node", "--version")

    isIgnoreExitValue = true

    doLast {
        if (executionResult.get().exitValue != 0) {
            throw GradleException("""
                ❌ Node.js is not installed!
                Please install Node.js from: https://nodejs.org/
            """.trimIndent())
        }
    }
}

// 安装前端依赖
val installFrontendDeps by tasks.registering(Exec::class) {
    group = "frontend"
    description = "Install frontend dependencies"

    dependsOn(checkNodeInstalled)

    workingDir = file("../frontend")
    commandLine(npmCommand, "install")

    // 只有当 package.json 改变或 node_modules 不存在时才执行
    inputs.file("../frontend/package.json")
    inputs.file("../frontend/package-lock.json")
    outputs.dir("../frontend/node_modules")

    // 🔧 禁用状态跟踪以避免 Windows 符号链接问题
    doNotTrackState("node_modules contains symbolic links on Windows that Gradle cannot snapshot")

    doFirst {
        println("📦 Installing frontend dependencies...")
    }
}

// ✅ Vue 前端构建任务 - 使用 Vite 构建
val buildFrontendWithVite by tasks.registering(Exec::class) {
    group = "frontend"
    description = "Build Vue frontend with Vite"

    dependsOn(installFrontendDeps)

    workingDir = file("../frontend")
    commandLine(npmCommand, "run", "build")

    // 输入：所有源文件
    inputs.dir("../frontend/src")
    inputs.file("../frontend/vite.config.ts")
    inputs.file("../frontend/tsconfig.json")
    inputs.file("../frontend/index.html")

    // 输出：前端 dist 目录
    outputs.dir("../frontend/dist")

    doFirst {
        println("🔨 Building Vue frontend with Vite...")
    }

    doLast {
        println("✅ Vue frontend built successfully")
        // 构建完成后复制到资源目录
        copy {
            from("../frontend/dist")
            into("src/main/resources/frontend")
        }
        println("📦 Frontend resources copied to resources/frontend")
    }
}

// 主构建任务 - 依赖 Vite 构建
val buildFrontend by tasks.registering {
    group = "frontend"
    description = "Build frontend (uses Vite)"

    dependsOn(buildFrontendWithVite)
}

// 清理前端构建产物
val cleanFrontend by tasks.registering(Delete::class) {
    group = "frontend"
    description = "Clean frontend build artifacts"

    delete("src/main/resources/frontend")
    delete("../frontend/dist")
    delete("../frontend/node_modules")
}

// ===== 集成到主构建流程 =====

tasks {
    // 在处理资源之前先构建前端
    processResources {
        dependsOn(buildFrontend)
    }

    // 清理时也清理前端
    clean {
        dependsOn(cleanFrontend)
    }

    runIde {
        // 确保运行前构建了前端
        dependsOn(buildFrontend)

        jvmArgs(
            "-Xmx2048m",
            "-Dfile.encoding=UTF-8",
            "-Dconsole.encoding=UTF-8",
            "-Dsun.stdout.encoding=UTF-8",
            "-Dsun.stderr.encoding=UTF-8"
        )
    }

    buildSearchableOptions {
        enabled = false
    }

    // 构建插件前先构建前端
    buildPlugin {
        dependsOn(buildFrontend)
    }
}