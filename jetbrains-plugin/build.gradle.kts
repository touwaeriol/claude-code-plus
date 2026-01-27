import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jetbrains.intellij.platform")
}

group = providers.gradleProperty("pluginGroup").get()

// ===== 动态构建目录支持（用于并行多版本构建）=====
// 通过 -PcustomBuildDir=/path/to/dir 指定自定义构建目录
providers.gradleProperty("customBuildDir").orNull?.let { customDir ->
    layout.buildDirectory.set(file(customDir))
}

// ===== 多版本构建支持 =====
// 通过 -PplatformMajor=242 指定目标平台版本
// 242 = 2024.2, 243 = 2024.3, 251 = 2025.1, 252 = 2025.2, 253 = 2025.3
// 默认使用最新版本 (253)
val platformMajor = providers.gradleProperty("platformMajor").getOrElse("253").toInt()

// ===== 版本号配置 =====
// 版本号始终带上平台后缀（如 1.2.1.253）
// 使用点分隔符而非连字符，避免被 SemVer 识别为 pre-release
// 这样可以上传多个版本到 Marketplace，IDE 会自动选择匹配的版本
val baseVersion = providers.gradleProperty("pluginVersion").get()
version = "$baseVersion.$platformMajor"

// 根据目标版本选择 IDE SDK 版本
// 构建时用对应版本的 SDK 编译，确保 API 兼容
val targetPlatformVersion = when (platformMajor) {
    242 -> "2024.2.6"
    243 -> "2024.3.5"
    251 -> "2025.1.5"
    252 -> "2025.2.4"
    else -> "2025.3.2"  // 253+
}

// ===== 兼容性代码目录配置 =====
// 按 API 变化点划分，避免反射，实现编译时类型安全

// 主兼容层目录 (VCS/Localization/BrowseButton API)
// - 242-252: 使用旧 API (kotlin-compat-242)
// - 253+: 使用新 API (kotlin-compat-253)
val mainCompatDir = when {
    platformMajor >= 253 -> "kotlin-compat-253"
    else -> "kotlin-compat-242"
}

// Terminal 兼容层目录
// - 242-243: 使用 createLocalShellWidget + LocalTerminalCustomizer (kotlin-compat-terminal-242-243)
// - 251-252: 使用 createNewSession API (kotlin-compat-terminal-251-252)
// - 253+: 使用 createNewSession API (kotlin-compat-terminal-253)
val terminalCompatDir = when {
    platformMajor >= 253 -> "kotlin-compat-terminal-253"
    platformMajor >= 251 -> "kotlin-compat-terminal-251-252"
    else -> "kotlin-compat-terminal-242-243"
}

// Diff API 兼容层目录
// - 242: 使用 DiffRequestProcessorEditor (kotlin-compat-diff-242)
// - 243+: 使用 DiffEditorViewerFileEditor (kotlin-compat-diff-243)
val diffCompatDir = when {
    platformMajor >= 243 -> "kotlin-compat-diff-243"
    else -> "kotlin-compat-diff-242"
}

// sinceBuild 和 untilBuild 配置
// 根据 platformMajor 确定版本范围
val targetSinceBuild = platformMajor.toString()

val targetUntilBuild = when {
    platformMajor >= 253 -> "253.*"
    platformMajor >= 252 -> "252.*"
    platformMajor >= 251 -> "251.*"
    platformMajor >= 243 -> "243.*"
    else -> "242.*"
}

// 配置 sourceSets 包含版本特定代码
sourceSets {
    main {
        kotlin {
            srcDir("src/main/kotlin")           // 通用代码
            srcDir("src/main/$mainCompatDir")   // 主兼容层 (VCS/Commit/Localization/JBCef)
            srcDir("src/main/$terminalCompatDir") // Terminal 兼容层
            srcDir("src/main/$diffCompatDir")   // Diff API 兼容层
        }
    }
}



dependencies {
    // ai-agent-server 依赖，排除 Logback 以使用 IDEA 内置的 SLF4J 实现
    // 这样 SLF4J 日志会自动写入 idea.log
    implementation(project(":ai-agent-server")) {
        exclude(group = "ch.qos.logback", module = "logback-classic")
        exclude(group = "ch.qos.logback", module = "logback-core")
        exclude(group = "org.slf4j", module = "jul-to-slf4j")
    }
    implementation(project(":ai-agent-proto")) // Protobuf 生成的类型
    // 添加 ai-agent-sdk 依赖 (包含 AiAgentProvider 等核心类型)
    implementation(project(":ai-agent-sdk"))





    // 添加 claude-agent-sdk 依赖
    implementation(project(":claude-agent-sdk"))

    // IntelliJ Platform dependencies
    intellijPlatform {
        // 🔧 多版本构建支持：根据 platformMajor 选择对应的 SDK 版本
        // 2025.3+ 使用 intellijIdea()，之前版本使用 intellijIdeaCommunity()
        if (platformMajor >= 253) {
            intellijIdea(targetPlatformVersion)
        } else {
            intellijIdeaCommunity(targetPlatformVersion)
        }

        // 🔧 添加 Java 插件依赖 (编译时需要，运行时通过 plugin.xml optional="true" 可选)
        // 这样可以在 IntelliJ IDEA 中使用完整功能，在 WebStorm/PyCharm 等非 Java IDE 中优雅降级
        bundledPlugin("com.intellij.java")

        // 🆕 添加 Terminal 插件依赖，用于 Terminal MCP 工具
        bundledPlugin("org.jetbrains.plugins.terminal")

        // 🆕 添加 Git4Idea 插件依赖 (编译时需要，运行时通过 plugin.xml optional="true" 可选)
        bundledPlugin("Git4Idea")

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

    // Markdown 渲染支持
    implementation("org.commonmark:commonmark:0.21.0")
    implementation("org.commonmark:commonmark-ext-gfm-tables:0.21.0")
    implementation("org.commonmark:commonmark-ext-gfm-strikethrough:0.21.0")

    // RSocket (over WebSocket) - 用于 JetBrains 双向通信
    val rsocketVersion = "0.20.0"
    implementation("io.rsocket.kotlin:rsocket-core:$rsocketVersion")

    // 统一日志模块 (SLF4J) - IDEA 内置 SLF4J 实现会将日志写入 idea.log
    implementation(project(":unified-logging"))

    // Ktor 服务器依赖 - 使用 3.0.3 版本（支持 SSE 和 WebSocket）
    val ktorVersion = "3.0.3"
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-cio:$ktorVersion")
    implementation("io.ktor:ktor-server-sse:$ktorVersion")
    implementation("io.ktor:ktor-server-websockets:$ktorVersion")  // ✅ WebSocket 支持
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-cors:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

}

// 🔧 排除 Logback 依赖，避免与 IDEA 内置 SLF4J 实现冲突
// - IDEA 内置 SLF4J 实现会将日志写入 idea.log
// - 保留 slf4j-api（unified-logging 需要）
configurations.all {
    exclude(group = "ch.qos.logback", module = "logback-classic")
    exclude(group = "ch.qos.logback", module = "logback-core")
    exclude(group = "org.slf4j", module = "jul-to-slf4j")
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
        version.set("$baseVersion.$platformMajor")

        ideaVersion {
            // 使用根据 platformMajor 计算的版本范围
            sinceBuild.set(targetSinceBuild)
            untilBuild.set(targetUntilBuild)
        }

        // 从 CHANGELOG.md 读取变更日志
        changeNotes.set(provider { extractLatestChangelog() })
    }

    // 插件兼容性验证配置 (2024.1 ~ 2025.3)
    // 支持多种 JetBrains IDE: IntelliJ IDEA, WebStorm, GoLand, CLion, PyCharm
    // 支持通过命令行参数指定单个 IDE 版本（用于 CI 分批验证）
    // 用法: ./gradlew verifyPlugin -PverifyIdeType=IC -PverifyIdeVersion=2024.1.7
    pluginVerification {
        // 忽略来自 optional dependency 的类（如 Java PSI 类、Terminal 插件类）
        // 这些类只在相应的插件存在时才会被使用，不会影响插件在其他 IDE 中的运行
        externalPrefixes.set(listOf(
            "com.intellij.psi",                      // Java PSI classes (used by JavaLanguageAnalysisService)
            "com.intellij.lang.jvm",                 // JVM language support
            "com.intellij.codeInsight",              // Code insight features
            "org.jetbrains.plugins.terminal"        // Terminal plugin classes (may not exist in 2024.1.x)
        ))

        ides {
            val verifyIdeType = providers.gradleProperty("verifyIdeType").orNull
            val verifyIdeVersion = providers.gradleProperty("verifyIdeVersion").orNull

            if (verifyIdeType != null && verifyIdeVersion != null) {
                // CI 分批验证模式:只验证指定的单个 IDE
                val ideType = when (verifyIdeType) {
                    "IC" -> IntelliJPlatformType.IntellijIdeaCommunity
                    "IU" -> IntelliJPlatformType.IntellijIdeaUltimate
                    "II" -> IntelliJPlatformType.IntellijIdea  // 2025.3+ 统一版本
                    "WS" -> IntelliJPlatformType.WebStorm
                    "GO" -> IntelliJPlatformType.GoLand
                    "CL" -> IntelliJPlatformType.CLion
                    "PC" -> IntelliJPlatformType.PyCharmCommunity
                    "PY" -> IntelliJPlatformType.PyCharmProfessional
                    "PS" -> IntelliJPlatformType.PhpStorm
                    else -> throw GradleException("Unknown IDE type: $verifyIdeType. Use IC, IU, II, WS, GO, CL, PC, PY, or PS")
                }
                create(ideType, verifyIdeVersion)
            } else {
                // 本地开发模式:验证所有关键版本

                // ===== IntelliJ IDEA =====
                // 2024.x 和 2025.1/2025.2 使用 IntellijIdeaCommunity
                create(IntelliJPlatformType.IntellijIdeaCommunity, "2024.2.6")  // 242
                create(IntelliJPlatformType.IntellijIdeaCommunity, "2024.3.5")  // 243
                create(IntelliJPlatformType.IntellijIdeaCommunity, "2025.1.5")  // 251
                create(IntelliJPlatformType.IntellijIdeaCommunity, "2025.2.4")  // 252
                // 2025.3+ 使用统一的 IntellijIdea 类型
                create(IntelliJPlatformType.IntellijIdea, "2025.3.1")           // 253

                // ===== WebStorm =====
                // 注意：WebStorm 版本号与 IDEA 不同，使用较保守的版本
                create(IntelliJPlatformType.WebStorm, "2024.2.4")
                create(IntelliJPlatformType.WebStorm, "2024.3.3")
                create(IntelliJPlatformType.WebStorm, "2025.1.2")
                create(IntelliJPlatformType.WebStorm, "2025.2.1")
                create(IntelliJPlatformType.WebStorm, "2025.3.1")

                // ===== GoLand =====
                // GoLand 的版本号与 IDEA 不同，例如 2024.2 最新是 2024.2.3
                create(IntelliJPlatformType.GoLand, "2024.2.3")
                create(IntelliJPlatformType.GoLand, "2024.3.3")
                create(IntelliJPlatformType.GoLand, "2025.1.2")
                create(IntelliJPlatformType.GoLand, "2025.2.1")
                create(IntelliJPlatformType.GoLand, "2025.3.1")

                // ===== CLion =====
                create(IntelliJPlatformType.CLion, "2024.2.3")
                create(IntelliJPlatformType.CLion, "2024.3.3")
                create(IntelliJPlatformType.CLion, "2025.1.2")
                create(IntelliJPlatformType.CLion, "2025.2.1")
                create(IntelliJPlatformType.CLion, "2025.3.1")

                // ===== PyCharm =====
                create(IntelliJPlatformType.PyCharmCommunity, "2024.2.4")
                create(IntelliJPlatformType.PyCharmCommunity, "2024.3.3")
                create(IntelliJPlatformType.PyCharmCommunity, "2025.1.2")
                create(IntelliJPlatformType.PyCharmCommunity, "2025.2.1")
                create(IntelliJPlatformType.PyCharmCommunity, "2025.3.1")

                // ===== PhpStorm =====
                create(IntelliJPlatformType.PhpStorm, "2024.2.4")
                create(IntelliJPlatformType.PhpStorm, "2024.3.3")
                create(IntelliJPlatformType.PhpStorm, "2025.1.2")
                create(IntelliJPlatformType.PhpStorm, "2025.2.1")
                create(IntelliJPlatformType.PhpStorm, "2025.3.1")
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
        osName.contains("windows") -> "cmd.exe"
        else -> System.getenv("SHELL") ?: "/bin/sh"
    }
}

/**
 * 在用户默认终端中执行命令
 */
fun shellCommand(command: String): List<String> {
    val shell = getDefaultShell()
    return when {
        shell.contains("cmd") -> listOf(shell, "/c", command)
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

        // 🔧 禁用 CDS 和类共享警告
        jvmArgs(
            "-Xshare:off",  // 禁用类数据共享,避免 CDS 警告
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:-PrintWarnings",  // 禁用 VM 警告输出
        )
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
            // 🔧 禁用 CDS 和类共享警告
            "-Xshare:off",  // 禁用类数据共享,避免 CDS 警告
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:-PrintWarnings",  // 禁用 VM 警告输出
        )
    }

    buildSearchableOptions {
        enabled = false
    }

    // 禁用 prepareJarSearchableOptions，因为 buildSearchableOptions 已禁用
    prepareJarSearchableOptions {
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

// ===== 多版本批量构建 =====
// 用法: gradlew :jetbrains-plugin:buildAllVersions
// 优化: 前端只构建一次，然后并行构建各版本插件（每个版本独立构建目录）

// 主任务：构建所有版本（串行执行，实时输出进度）
val buildAllVersions by tasks.registering {
    group = "build"
    description = "Build plugin for all supported platform versions (242, 243, 251, 252, 253)"

    // 先构建前端和下载 CLI（只执行一次）
    dependsOn(buildFrontend)
    dependsOn(":claude-agent-sdk:downloadCli")

    // 将需要的值在配置阶段捕获，避免配置缓存问题
    val buildDir = layout.buildDirectory
    val projectDir = rootProject.projectDir
    val versionStr = baseVersion  // 使用不带后缀的基础版本号

    doFirst {
        println("====================================")
        println("Building plugin for all platforms")
        println("====================================")
        println()
        println("📦 Frontend built once, reusing for all platforms")
        println("🔄 Building 5 versions sequentially with live output...")
        println()
    }

    doLast {
        // 在执行阶段定义所有变量，避免配置缓存序列化问题
        val platforms = listOf("242", "243", "251", "252", "253")
        val isWin = System.getProperty("os.name").lowercase().contains("windows")
        val gradlew = if (isWin) File(projectDir, "gradlew.bat").absolutePath else File(projectDir, "gradlew").absolutePath

        val distDir = buildDir.dir("distributions").get().asFile
        val tempBuildDir = buildDir.dir("multi-version-temp").get().asFile

        // 清理临时目录
        if (tempBuildDir.exists()) {
            tempBuildDir.deleteRecursively()
        }
        tempBuildDir.mkdirs()

        val failedPlatforms = mutableListOf<String>()

        // 串行构建每个版本，实时输出进度
        platforms.forEachIndexed { index, platform ->
            val platformBuildDir = File(tempBuildDir, platform)
            platformBuildDir.mkdirs()

            println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            println("📌 [${index + 1}/5] Building platform $platform...")
            println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

            val cmd = if (isWin) {
                listOf("cmd", "/c", gradlew, ":jetbrains-plugin:buildPlugin",
                    "-PplatformMajor=$platform",
                    "-PcustomBuildDir=${platformBuildDir.absolutePath}",
                    "-x", "buildFrontend",
                    "-x", "buildFrontendWithVite",
                    "-x", "copyFrontendFiles",
                    "-x", "installFrontendDeps",
                    "--no-daemon", "--console=plain")
            } else {
                listOf(gradlew, ":jetbrains-plugin:buildPlugin",
                    "-PplatformMajor=$platform",
                    "-PcustomBuildDir=${platformBuildDir.absolutePath}",
                    "-x", "buildFrontend",
                    "-x", "buildFrontendWithVite",
                    "-x", "copyFrontendFiles",
                    "-x", "installFrontendDeps",
                    "--no-daemon", "--console=plain")
            }

            val processBuilder = ProcessBuilder(cmd)
                .directory(projectDir)
                .redirectErrorStream(true)

            val process = processBuilder.start()

            // 实时读取并输出进度
            val reader = process.inputStream.bufferedReader()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                println("  [$platform] $line")
            }

            val exitCode = process.waitFor()

            if (exitCode != 0) {
                println("❌ [$platform] BUILD FAILED (exit code: $exitCode)")
                failedPlatforms.add(platform)
            } else {
                println("✅ [$platform] BUILD SUCCESS")
            }
            println()
        }

        // 检查是否有失败
        if (failedPlatforms.isNotEmpty()) {
            throw GradleException("Build failed for platforms: ${failedPlatforms.joinToString(", ")}")
        }

        // 复制构建产物到最终目录
        println("====================================")
        println("📦 Collecting build artifacts...")
        println("====================================")
        distDir.mkdirs()
        platforms.forEach { platform ->
            val platformDistDir = File(tempBuildDir, "$platform/distributions")
            // 子构建生成的 zip 已经带版本后缀（如 1.2.1.253）
            val srcFile = File(platformDistDir, "claude-code-plus-jetbrains-plugin-${versionStr}.${platform}.zip")
            val dstFile = File(distDir, "claude-code-plus-jetbrains-plugin-${versionStr}.${platform}.zip")

            if (srcFile.exists()) {
                srcFile.copyTo(dstFile, overwrite = true)
                println("  ✅ [$platform] claude-code-plus-jetbrains-plugin-${versionStr}.${platform}.zip")
            } else {
                throw GradleException("[$platform] Output file not found: ${srcFile.absolutePath}")
            }
        }

        // 清理临时目录
        tempBuildDir.deleteRecursively()

        println()
        println("====================================")
        println("All 5 versions built successfully!")
        println("Output: jetbrains-plugin/build/distributions/")
        println("====================================")
    }
}

// ===== 多版本批量发布 =====
// 用法: gradlew :jetbrains-plugin:publishAllVersions
// 前提: 需要在 ~/.gradle/gradle.properties 中配置 intellijPlatformPublishingToken
val publishAllVersions by tasks.registering {
    group = "publishing"
    description = "Publish plugin for all supported platform versions (242, 243, 251, 252, 253) to JetBrains Marketplace"

    // 将需要的值在配置阶段捕获
    val projectDir = rootProject.projectDir

    doFirst {
        println("====================================")
        println("Publishing plugin for all platforms")
        println("====================================")
        println()
    }

    doLast {
        val platforms = listOf("242", "243", "251", "252", "253")
        val isWin = System.getProperty("os.name").lowercase().contains("windows")
        val gradlew = if (isWin) File(projectDir, "gradlew.bat").absolutePath else File(projectDir, "gradlew").absolutePath

        // 依次发布每个版本（JetBrains Marketplace 不支持并行上传）
        val results = mutableListOf<Pair<String, Int>>()

        for (platform in platforms) {
            println("📤 [$platform] Publishing...")

            val cmd = if (isWin) {
                listOf("cmd", "/c", gradlew, ":jetbrains-plugin:publishPlugin",
                    "-PplatformMajor=$platform",
                    "--no-daemon")
            } else {
                listOf(gradlew, ":jetbrains-plugin:publishPlugin",
                    "-PplatformMajor=$platform",
                    "--no-daemon")
            }

            val process = ProcessBuilder(cmd)
                .directory(projectDir)
                .redirectErrorStream(true)
                .start()

            // 实时输出日志
            process.inputStream.bufferedReader().forEachLine { line ->
                println("    [$platform] $line")
            }

            val exitCode = process.waitFor()

            if (exitCode == 0) {
                println("✅ [$platform] Published successfully!")
            } else {
                println("❌ [$platform] FAILED (exit code: $exitCode)")
            }

            results.add(platform to exitCode)
            println()
        }

        // 汇总结果
        println("====================================")
        println("Publishing Summary:")
        println("====================================")

        val succeeded = results.filter { it.second == 0 }
        val failed = results.filter { it.second != 0 }

        succeeded.forEach { (platform, _) ->
            println("✅ [$platform] Success")
        }
        failed.forEach { (platform, code) ->
            println("❌ [$platform] Failed (exit code: $code)")
        }

        println()
        println("Total: ${succeeded.size}/5 published successfully")

        if (failed.isNotEmpty()) {
            throw GradleException("Publishing failed for platforms: ${failed.map { it.first }.joinToString(", ")}")
        }
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