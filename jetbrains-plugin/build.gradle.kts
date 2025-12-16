import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jetbrains.intellij.platform")
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()



dependencies {
    implementation(project(":ai-agent-server"))
    implementation(project(":ai-agent-proto")) // Protobuf 生成的类型
    // 添加 ai-agent-sdk 依赖 (包含 AiAgentProvider 等核心类型)
    implementation(project(":ai-agent-sdk"))





    // 添加 claude-agent-sdk 依赖
    implementation(project(":claude-agent-sdk"))

    // IntelliJ Platform dependencies
    intellijPlatform {
        // 🔧 使用具体的方法而不是通用的 create()，以支持 runIde 任务
        // 从 2025.3 开始，IC/IU 合并为统一版本，使用 intellijIdea()
        intellijIdea(providers.gradleProperty("platformVersion").get())

        // 🔧 添加 Java 插件依赖，用于 ClassInheritorsSearch、OverridingMethodsSearch 等 API
        bundledPlugin("com.intellij.java")

        // UI 框架说明：
        // 本项目使用 Swing + IntelliJ JB UI 组件（官方推荐方案）
        // 可选使用 Kotlin UI DSL (com.intellij.ui.dsl.builder.*) - 已内置在 IntelliJ Platform 中，无需额外依赖
        // 不使用 Compose Multiplatform 或 Jewel（未使用相关 API）
    }

    // 使用 IntelliJ Platform 的 Kotlin 标准库
    compileOnly(kotlin("stdlib"))

    // 🔧 编译时需要协程 API，但运行时会被排除，使用 IntelliJ Platform 内置版本
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${rootProject.extra["coroutinesVersion"]}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:${rootProject.extra["coroutinesVersion"]}")

    // 🔧 Kotlin serialization 运行时依赖
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${rootProject.extra["serializationVersion"]}")

    // Hutool 反射工具 - 用于可选依赖的反射调用
    implementation("cn.hutool:hutool-core:5.8.25")

    // Markdown 渲染支持
    implementation("org.commonmark:commonmark:0.21.0")
    implementation("org.commonmark:commonmark-ext-gfm-tables:0.21.0")
    implementation("org.commonmark:commonmark-ext-gfm-strikethrough:0.21.0")

    // RSocket (over WebSocket) - 用于 JetBrains 双向通信
    val rsocketVersion = "0.20.0"
    implementation("io.rsocket.kotlin:rsocket-core:$rsocketVersion")

    // Logging (用于 JetBrainsRSocketHandler)
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")

    // Ktor 服务器依赖 - 使用 3.0.3 版本（支持 SSE 和 WebSocket）
    val ktorVersion = "3.0.3"
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-cio:$ktorVersion")
    implementation("io.ktor:ktor-server-sse:$ktorVersion")
    implementation("io.ktor:ktor-server-websockets:$ktorVersion")  // ✅ WebSocket 支持
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-cors:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

    // 测试依赖 - 使用 compileOnly 避免与 IDE 内置版本冲突
    testCompileOnly(kotlin("stdlib"))
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation(kotlin("test-junit5"))
}

// 从 CHANGELOG.md 提取最新版本的变更日志
fun extractLatestChangelog(): String {
    val changelogFile = file("../CHANGELOG.md")
    if (!changelogFile.exists()) return "<p>See CHANGELOG.md for details</p>"

    val content = changelogFile.readText()
    val versionRegex = Regex("""## \[[\d.]+\].*?(?=## \[|\Z)""", RegexOption.DOT_MATCHES_ALL)
    val latestSection = versionRegex.find(content)?.value ?: return "<p>See CHANGELOG.md for details</p>"

    // 转换 Markdown 为简单 HTML
    return latestSection
        .replace(Regex("""## \[([\d.]+)\] - (.+)"""), "<h3>Version $1 ($2)</h3>")
        .replace(Regex("""### (.+)"""), "<h4>$1</h4>")
        .replace(Regex("""^- (.+)$""", RegexOption.MULTILINE), "<li>$1</li>")
        .replace(Regex("""(<li>.*</li>\n?)+""")) { "<ul>${it.value}</ul>" }
        .trim()
}

// IntelliJ 平台配置
intellijPlatform {
    pluginConfiguration {
        name.set(providers.gradleProperty("pluginName"))
        version.set(providers.gradleProperty("pluginVersion"))

        ideaVersion {
            sinceBuild.set(providers.gradleProperty("pluginSinceBuild"))
            untilBuild.set(providers.gradleProperty("pluginUntilBuild"))
        }

        // 从 CHANGELOG.md 读取变更日志
        changeNotes.set(provider { extractLatestChangelog() })
    }

    // 插件兼容性验证配置 (2024.2 ~ 2025.3)
    // 支持通过命令行参数指定单个 IDE 版本（用于 CI 分批验证）
    // 用法: ./gradlew verifyPlugin -PverifyIdeType=IC -PverifyIdeVersion=2024.2.6
    pluginVerification {
        ides {
            val verifyIdeType = providers.gradleProperty("verifyIdeType").orNull
            val verifyIdeVersion = providers.gradleProperty("verifyIdeVersion").orNull

            if (verifyIdeType != null && verifyIdeVersion != null) {
                // CI 分批验证模式：只验证指定的单个 IDE
                val ideType = when (verifyIdeType) {
                    "IC" -> IntelliJPlatformType.IntellijIdeaCommunity
                    "IU" -> IntelliJPlatformType.IntellijIdeaUltimate
                    "II" -> IntelliJPlatformType.IntellijIdea  // 2025.3+ 统一版本
                    else -> throw GradleException("Unknown IDE type: $verifyIdeType. Use IC, IU, or II")
                }
                ide(ideType, verifyIdeVersion)
            } else {
                // 本地开发模式：验证所有关键版本
                // 2024.x 和 2025.1/2025.2 使用 IntellijIdeaCommunity
                ide(IntelliJPlatformType.IntellijIdeaCommunity, "2024.2.6")
                ide(IntelliJPlatformType.IntellijIdeaCommunity, "2024.3.5")
                ide(IntelliJPlatformType.IntellijIdeaCommunity, "2025.1.5")
                ide(IntelliJPlatformType.IntellijIdeaCommunity, "2025.2.4")
                // 2025.3+ 使用统一的 IntellijIdea 类型
                ide(IntelliJPlatformType.IntellijIdea, "2025.3")
            }
        }
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

/**
 * 获取用户默认终端
 */
fun getDefaultShell(): String {
    val osName = System.getProperty("os.name").lowercase()
    return when {
        osName.contains("windows") -> "powershell.exe"
        else -> System.getenv("SHELL") ?: "/bin/sh"
    }
}

/**
 * 在用户默认终端中执行命令
 */
fun shellCommand(command: String): List<String> {
    val shell = getDefaultShell()
    return when {
        shell.contains("powershell") -> listOf(shell, "-Command", command)
        else -> listOf(shell, "-c", command)
    }
}

// 检查 Node.js 是否安装（可选检查，不阻塞构建）
val checkNodeInstalled by tasks.registering(Exec::class) {
    group = "frontend"
    description = "Check if Node.js is installed (optional check)"

    commandLine("node", "--version")

    isIgnoreExitValue = true

    doLast {
        if (executionResult.get().exitValue != 0) {
            logger.warn("""
                ⚠️ Node.js not found in Gradle's PATH
                This is normal if you use NVM or custom Node installation.
                Frontend build will use npm/node from system PATH.
            """.trimIndent())
        } else {
            logger.lifecycle("✅ Node.js found in PATH")
        }
    }
}

// 安装前端依赖
val installFrontendDeps by tasks.registering(Exec::class) {
    group = "frontend"
    description = "Install frontend dependencies"

    val shell = getDefaultShell()
    val npmCmd = shellCommand("npm install --legacy-peer-deps")

    workingDir = file("../frontend")
    commandLine(*npmCmd.toTypedArray())

    // 只有当 package.json 改变或 node_modules 不存在时才执行
    inputs.file("../frontend/package.json")
    outputs.dir("../frontend/node_modules")

    // 🔧 禁用状态跟踪以避免 Windows 符号链接问题
    doNotTrackState("node_modules contains symbolic links on Windows that Gradle cannot snapshot")

    doFirst {
        println("📦 Installing frontend dependencies via $shell...")
    }
}

// ✅ Vue 前端构建任务 - 生产模式（terser 压缩 + gzip/brotli）
val buildFrontendWithVite by tasks.registering(Exec::class) {
    group = "frontend"
    description = "Build Vue frontend with Vite (production mode with full optimization)"

    val npmCmd = shellCommand("npm run build")

    dependsOn(installFrontendDeps)

    workingDir = file("../frontend")
    commandLine(*npmCmd.toTypedArray())

    // 输入：所有源文件
    inputs.dir("../frontend/src")
    inputs.file("../frontend/vite.config.ts")
    inputs.file("../frontend/tsconfig.json")
    inputs.file("../frontend/index.html")

    // 输出：前端 dist 目录
    outputs.dir("../frontend/dist")

    // 🔧 禁用增量构建缓存 - 确保前端修改总是生效
    outputs.upToDateWhen { false }

    doFirst {
        println("🔨 Building Vue frontend with Vite (production mode)...")
    }

    doLast {
        println("✅ Vue frontend built successfully (optimized)")
    }
}

// ✅ Vue 前端构建任务 - 开发模式（无压缩，构建更快）
val buildFrontendWithViteDev by tasks.registering(Exec::class) {
    group = "frontend"
    description = "Build Vue frontend with Vite (development mode, no optimization)"

    val npmCmd = shellCommand("npm run build:dev")

    dependsOn(installFrontendDeps)

    workingDir = file("../frontend")
    commandLine(*npmCmd.toTypedArray())

    // 输入：所有源文件
    inputs.dir("../frontend/src")
    inputs.file("../frontend/vite.config.ts")
    inputs.file("../frontend/tsconfig.json")
    inputs.file("../frontend/index.html")

    // 输出：前端 dist 目录
    outputs.dir("../frontend/dist")

    // 🔧 禁用增量构建缓存 - 确保前端修改总是生效
    outputs.upToDateWhen { false }

    doFirst {
        println("🔨 Building Vue frontend with Vite (development mode)...")
    }

    doLast {
        println("✅ Vue frontend built successfully (dev mode)")
    }
}

// 主构建任务 - 依赖 Vite 构建（生产模式）
val copyFrontendFiles by tasks.registering(Copy::class) {
    group = "frontend"
    description = "Copy frontend build artifacts to resources (production)"

    dependsOn(buildFrontendWithVite)
    // 确保不会与开发模式构建冲突
    mustRunAfter(buildFrontendWithViteDev)

    // 🔧 使用 layout API 来避免配置缓存问题
    val frontendDistDir = layout.projectDirectory.dir("../frontend/dist")
    val targetDir = layout.projectDirectory.dir("src/main/resources/frontend")

    from(frontendDistDir)
    into(targetDir)

    // 🔧 修复 Windows 文件被占用问题
    duplicatesStrategy = DuplicatesStrategy.INCLUDE

    // 🔧 在复制前删除目标目录，避免文件占用冲突
    doFirst {
        val targetFile = targetDir.asFile
        if (targetFile.exists()) {
            println("🗑️  Deleting existing frontend resources...")
            targetFile.deleteRecursively()
        }
    }

    doLast {
        println("📦 Frontend resources copied to resources/frontend (production)")
    }
}

// 开发模式复制任务
val copyFrontendFilesDev by tasks.registering(Copy::class) {
    group = "frontend"
    description = "Copy frontend build artifacts to resources (development)"

    dependsOn(buildFrontendWithViteDev)
    // 确保不会与生产模式构建冲突
    mustRunAfter(buildFrontendWithVite)

    val frontendDistDir = layout.projectDirectory.dir("../frontend/dist")
    val targetDir = layout.projectDirectory.dir("src/main/resources/frontend")

    from(frontendDistDir)
    into(targetDir)

    duplicatesStrategy = DuplicatesStrategy.INCLUDE

    doFirst {
        val targetFile = targetDir.asFile
        if (targetFile.exists()) {
            println("🗑️  Deleting existing frontend resources...")
            targetFile.deleteRecursively()
        }
    }

    doLast {
        println("📦 Frontend resources copied to resources/frontend (dev mode)")
    }
}

// 主构建任务 - 生产模式（用于发布）
val buildFrontend by tasks.registering {
    group = "frontend"
    description = "Build frontend and copy files (production)"

    dependsOn(copyFrontendFiles)
}

// 开发构建任务（用于 runIde）
val buildFrontendDev by tasks.registering {
    group = "frontend"
    description = "Build frontend and copy files (development, faster)"

    dependsOn(copyFrontendFilesDev)
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
    // 配置测试任务使用 JUnit Platform
    test {
        useJUnitPlatform()
    }

    // processResources 不自动依赖前端构建，由具体任务决定
    // runIde 使用开发模式，buildPlugin 使用生产模式
    processResources {
        mustRunAfter(copyFrontendFiles, copyFrontendFilesDev)
    }

    // 清理时也清理前端
    clean {
        dependsOn(cleanFrontend)
    }

    runIde {
        // 确保运行前下载了 CLI（来自 claude-agent-sdk 模块）
        dependsOn(":claude-agent-sdk:downloadCli")
        // 确保运行前构建了前端（开发模式，无压缩，更快）
        dependsOn(buildFrontendDev)

        // 🔧 增加内存配置以避免 OOM
        jvmArgs(
            "-Xmx4096m",  // 堆内存从 2GB 增加到 4GB
            "-XX:MaxMetaspaceSize=1024m",  // 元空间增加到 1GB
            "-XX:ReservedCodeCacheSize=512m",  // 代码缓存增加
            "-XX:+UseG1GC",  // 使用 G1 垃圾收集器
            "-Dfile.encoding=UTF-8",
            "-Dconsole.encoding=UTF-8",
            "-Dsun.stdout.encoding=UTF-8",
            "-Dsun.stderr.encoding=UTF-8",
        )
    }

    buildSearchableOptions {
        enabled = false
    }

    // 构建插件前先下载 CLI 并构建前端
    buildPlugin {
        dependsOn(":claude-agent-sdk:downloadCli")
        dependsOn(buildFrontend)
        // 设置输出文件名
        archiveBaseName.set("claude-code-plus-jetbrains-plugin")
    }
}

// 🔧 对于插件模块，只排除运行时的 kotlinx-coroutines，保留编译时
configurations {
    // 只排除运行时配置，保留编译时配置
    named("runtimeClasspath") {
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core-jvm")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-swing")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-debug")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-test")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-jdk8")
    }
}