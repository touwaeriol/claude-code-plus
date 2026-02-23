import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Jar
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.dokka.gradle.DokkaTask
import java.net.URL
import java.net.URI
import java.net.Proxy
import java.net.InetSocketAddress
import java.net.URLConnection
import java.security.MessageDigest
import java.util.Properties
import java.io.InputStream
import java.io.OutputStream

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("java-library")
    id("maven-publish")
    id("signing")
    id("org.jetbrains.dokka")
}

group = "com.asakii"
version = "0.1.0"

val ossrhUsername: String? by project
val ossrhPassword: String? by project
val isSnapshotRelease = version.toString().endsWith("SNAPSHOT")

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    // Kotlin 标准库和协程
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // 统一日志模块 (SLF4J)
    api(project(":unified-logging"))

    // JSON 序列化
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    implementation("org.jetbrains.kotlinx:kotlinx-rpc-krpc-ktor-client:0.10.1") // kRPC over Ktor client



    // Hutool 缓存和加密工具
    implementation("cn.hutool:hutool-cache:5.8.25")
    implementation("cn.hutool:hutool-crypto:5.8.25")

}

kotlin {
    jvmToolchain(17)
}

// 🔧 配置 Dokka 任务以支持配置缓存
tasks.named<Jar>("javadocJar") {
    val dokkaJavadoc = tasks.named<DokkaTask>("dokkaJavadoc")
    dependsOn(dokkaJavadoc)
    // 使用 Provider API 延迟解析,避免配置缓存问题
    from(dokkaJavadoc.map { it.outputDirectory })
}

fun MavenPublication.configureCommonPom(displayName: String, moduleDescription: String) {
    pom {
        name.set(displayName)
        description.set(moduleDescription)
        url.set("https://github.com/asakii/claude-code-plus")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
            }
        }
        developers {
            developer {
                id.set("asakii")
                name.set("Asakii Team")
                email.set("opensource@asakii.com")
            }
        }
        scm {
            connection.set("scm:git:https://github.com/asakii/claude-code-plus.git")
            developerConnection.set("scm:git:ssh://git@github.com:asakii/claude-code-plus.git")
            url.set("https://github.com/asakii/claude-code-plus")
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("claudeAgentSdkKotlin") {
            from(components["java"])
            artifactId = "claude-agent-sdk-kotlin"
            configureCommonPom(
                displayName = "Claude Agent SDK (Kotlin)",
                moduleDescription = "Kotlin-first SDK for integrating Claude Agents."
            )
        }
        create<MavenPublication>("claudeAgentSdkJava") {
            from(components["java"])
            artifactId = "claude-agent-sdk-java"
            configureCommonPom(
                displayName = "Claude Agent SDK (Java)",
                moduleDescription = "Java-friendly distribution of the Claude Agent SDK with the same JVM bytecode."
            )
        }
    }
    repositories {
        maven {
            name = "sonatype"
            url = uri(
                if (isSnapshotRelease) {
                    "https://s01.oss.sonatype.org/content/repositories/snapshots/"
                } else {
                    "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
                }
            )
            credentials {
                username = ossrhUsername ?: System.getenv("OSSRH_USERNAME")
                password = ossrhPassword ?: System.getenv("OSSRH_PASSWORD")
            }
        }
    }
}

signing {
    useGpgCmd()
    isRequired = !version.toString().endsWith("SNAPSHOT")
    sign(publishing.publications)
}
// 运行示例的任务
tasks.register<JavaExec>("runModelTest") {
    group = "verification"


    description = "运行模型切换测试示例"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.asakii.claude.agent.sdk.examples.ModelIdentificationTestKt")
    standardInput = System.`in`
}

tasks.register<JavaExec>("runSonnet45Test") {
    group = "verification"
    description = "测试切换到 Sonnet 4.5"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.asakii.claude.agent.sdk.examples.SwitchToSonnet45TestKt")
    standardInput = System.`in`
}

tasks.register<JavaExec>("runSlashCommandTest") {
    group = "verification"
    description = "测试 /model 斜杠命令"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.asakii.claude.agent.sdk.examples.SlashCommandModelTestKt")
    standardInput = System.`in`
}

tasks.register<JavaExec>("runOpusTest") {
    group = "verification"
    description = "测试切换到 Opus 模型"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.asakii.claude.agent.sdk.examples.OpusSwitchTestKt")
    standardInput = System.`in`
}

tasks.register<JavaExec>("runJointTestClient") {
    group = "verification"
    description = "Runs the joint test client to connect to a running server"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.asakii.claude.agent.sdk.examples.JointTestClientKt")
}

tasks.register<JavaExec>("runQuickConnectionTest") {
    group = "verification"
    description = "快速测试 SDK 连接"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.asakii.claude.agent.sdk.examples.QuickConnectionTestKt")
    standardInput = System.`in`
}

// ========== CLI 绑定任务 ==========

// 读取 CLI 版本配置（统一在 cli-version.properties 中配置）
val cliVersionProps = Properties().apply {
    file("cli-version.properties").inputStream().use { load(it) }
}
val cliVersion = cliVersionProps.getProperty("cli.version")
    ?: error("cli.version is missing in cli-version.properties")
val npmVersion = cliVersionProps.getProperty("npm.version")
    ?: error("npm.version is missing in cli-version.properties")

// 定义资源目录
val bundledDir = file("src/main/resources/bundled")

// MD5 校验值 (版本 2.0.65)
val expectedMd5: Map<String, String> = mapOf(
    // 暂未发布原生二进制校验值，后续发布后补充
)

// MD5 校验辅助函数
fun calculateMd5(file: File): String {
    val md = MessageDigest.getInstance("MD5")
    file.inputStream().use { input ->
        val buffer = ByteArray(8192)
        var read: Int
        while (input.read(buffer).also { read = it } > 0) {
            md.update(buffer, 0, read)
        }
    }
    return md.digest().joinToString("") { String.format("%02x", it) }
}

fun verifyMd5(file: File, expectedMd5: String): Boolean {
    val actualMd5 = calculateMd5(file)
    return actualMd5.equals(expectedMd5, ignoreCase = true)
}

// 下载 CLI 任务 - 从 npm 包下载 cli.js 到 cli-patches 目录（用于打补丁）
val downloadCli = tasks.register("downloadCli") {
    group = "build"
    description = "从 npm 包下载 Claude CLI 到 cli-patches 目录"

    val propsFile = file("cli-version.properties")
    val cliPatchesDirFile = file("cli-patches")  // 改为 cli-patches 目录
    val buildDirFile = layout.buildDirectory.get().asFile

    inputs.file(propsFile)
    outputs.dir(cliPatchesDirFile)

    // 确保 CLI 文件实际存在，而不仅仅是目录存在
    outputs.upToDateWhen {
        val props = Properties()
        propsFile.inputStream().use { props.load(it) }
        val cliVer = props.getProperty("cli.version") ?: return@upToDateWhen false
        val cliJsFile = cliPatchesDirFile.resolve("claude-cli-$cliVer.js")
        cliJsFile.exists() && cliJsFile.length() > 0
    }

    doLast {
        val props = Properties()
        propsFile.inputStream().use { props.load(it) }
        val cliVer = props.getProperty("cli.version") ?: error("cli.version missing")
        val npmVer = props.getProperty("npm.version") ?: error("npm.version missing")

        val cliJsFile = cliPatchesDirFile.resolve("claude-cli-$cliVer.js")
        if (cliJsFile.exists()) {
            println("⏭️  claude-cli-$cliVer.js 已存在于 cli-patches，跳过下载")
            return@doLast
        }

        cliPatchesDirFile.mkdirs()

        // 清理旧版本 cli.js（只清理原始版本，不清理 enhanced 版本）
        cliPatchesDirFile.listFiles { file ->
            file.name.startsWith("claude-cli-") &&
            file.name.endsWith(".js") &&
            !file.name.contains("-enhanced") &&
            file.name != cliJsFile.name
        }?.forEach { old ->
            println("🧹 检测到旧版本 CLI: ${old.name}，已删除")
            old.delete()
        }

        println("========================================")
        println("下载 Claude CLI (cli.js) 版本: $cliVer")
        println("========================================")

        try {
            val npmTarballUrl = "https://registry.npmjs.org/@anthropic-ai/claude-agent-sdk/-/claude-agent-sdk-$npmVer.tgz"

            println("📦 npm 包版本: $npmVer")
            println("📥 下载中...")
            println("   URL: $npmTarballUrl")

            val tarballFile = File(buildDirFile, "tmp/claude-cli/claude-agent-sdk.tgz")
            tarballFile.parentFile.mkdirs()

            val connection: URLConnection = URI(npmTarballUrl).toURL().openConnection()
            connection.connectTimeout = 30000
            connection.readTimeout = 300000
            connection.getInputStream().use { input: InputStream ->
                tarballFile.outputStream().use { output: OutputStream ->
                    input.copyTo(output)
                }
            }

            println("   ✅ tarball 下载完成")

            val extractDir = File(buildDirFile, "tmp/claude-cli/extract")
            extractDir.mkdirs()

            println("📂 解压 tarball...")
            val process = ProcessBuilder("tar", "-xzf", tarballFile.absolutePath)
                .directory(extractDir)
                .redirectErrorStream(true)
                .start()
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                throw GradleException("解压失败，退出码: $exitCode")
            }

            val sourceCliJs = extractDir.resolve("package/cli.js")
            if (!sourceCliJs.exists()) {
                throw GradleException("未找到 cli.js 在解压的包中")
            }

            sourceCliJs.copyTo(cliJsFile, overwrite = true)

            val sizeMB = cliJsFile.length() / (1024.0 * 1024.0)
            println("   大小: ${String.format("%.2f", sizeMB)} MB")
            println("   ✅ cli.js 提取成功: ${cliJsFile.name}")

            tarballFile.delete()
            extractDir.deleteRecursively()

            println("\n========================================")
            println("✅ 下载完成！")
            println("   文件: cli-patches/${cliJsFile.name}")
            println("========================================")

        } catch (e: Exception) {
            println("❌ 下载失败: ${e.message}")
            e.printStackTrace()
            throw GradleException("CLI 下载失败", e)
        }
    }
}

// 清理 CLI 文件
val cleanCli = tasks.register("cleanCli") {
    group = "build"
    description = "清理 CLI 文件（bundled 和 cli-patches 目录）"

    val bundledDirFile = file("src/main/resources/bundled")
    val cliPatchesDirFile = file("cli-patches")

    doLast {
        // 清理 bundled 目录（增强版 CLI）
        bundledDirFile.listFiles { file -> file.name.startsWith("claude-cli-") }?.forEach {
            it.delete()
            println("🧹 已删除: bundled/${it.name}")
        }
        // 清理 cli-patches 目录（原始 CLI）
        cliPatchesDirFile.listFiles { file ->
            file.name.startsWith("claude-cli-") && file.name.endsWith(".js")
        }?.forEach {
            it.delete()
            println("🧹 已删除: cli-patches/${it.name}")
        }
        println("✅ 已清理 CLI 文件")
    }
}

// 校验 CLI MD5
val verifyCli = tasks.register("verifyCli") {
    group = "verification"
    description = "校验已下载的 CLI 文件 MD5"

    val propsFile = file("cli-version.properties")
    val bundledDirFile = file("src/main/resources/bundled")

    inputs.file(propsFile)
    inputs.dir(bundledDirFile).optional()

    doLast {
        val versionProps = Properties()
        propsFile.inputStream().use { versionProps.load(it) }
        val version = versionProps.getProperty("cli.version") ?: "unknown"

        val md5Map: Map<String, String> = mapOf()

        println("========================================")
        println("校验 CLI MD5 (版本: $version)")
        println("========================================")

        var passCount = 0
        var failCount = 0
        var missingCount = 0

        md5Map.forEach { (fileKey, expectedHash) ->
            val filePath = bundledDirFile.resolve(fileKey)

            if (!filePath.exists()) {
                println("⏭️  跳过 $fileKey (文件不存在)")
                missingCount++
                return@forEach
            }

            print("🔐 校验 $fileKey... ")
            val actualMd5 = calculateMd5(filePath)
            if (actualMd5.equals(expectedHash, ignoreCase = true)) {
                println("✅ 通过")
                passCount++
            } else {
                println("❌ 失败")
                println("   期望: $expectedHash")
                println("   实际: $actualMd5")
                failCount++
            }
        }

        println("\n========================================")
        println("校验汇总:")
        println("  ✅ 通过: $passCount")
        println("  ❌ 失败: $failCount")
        println("  ⏭️  缺失: $missingCount")
        println("========================================")

        if (failCount > 0) {
            throw GradleException("MD5 校验失败，有 $failCount 个文件不匹配")
        }
    }
}

// 复制 cli-version.properties 到 resources 目录
val copyCliVersionProps = tasks.register<Copy>("copyCliVersionProps") {
    group = "build"
    description = "复制 cli-version.properties 到 resources 目录"
    from(file("cli-version.properties"))
    into(file("src/main/resources"))
}

// 注意: clean 任务不再自动清理 CLI 文件
// 如需清理 CLI 文件（如升级 CLI 版本时），请手动运行: ./gradlew cleanCli

// ========== CLI 补丁系统 (AST 转换) ==========

// 安装 AST 补丁依赖
val installPatchDeps = tasks.register("installPatchDeps") {
    group = "build"
    description = "安装 AST 补丁脚本的 npm 依赖"
    dependsOn(downloadCli)  // 显式声明依赖，确保 cli-patches/package.json 已存在

    // 将路径捕获到局部变量，避免 configuration cache 问题
    val patchesDirPath = file("cli-patches").absolutePath
    val packageJsonPath = file("cli-patches/package.json").absolutePath
    val nodeModulesPath = file("cli-patches/node_modules").absolutePath

    inputs.file(packageJsonPath)
    outputs.dir(nodeModulesPath)

    onlyIf { File(packageJsonPath).exists() }

    doLast {
        val patchesDir = File(patchesDirPath)
        val nodeModules = File(nodeModulesPath)

        if (nodeModules.exists() && nodeModules.resolve("@babel/parser").exists()) {
            println("⏭️  npm 依赖已安装，跳过")
            return@doLast
        }

        println("📦 安装 AST 补丁依赖...")
        val process = ProcessBuilder("npm", "install")
            .directory(patchesDir)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            println(output)
            throw GradleException("npm install 失败")
        }
        println("   ✅ 依赖安装完成")
    }
}

// 使用 AST 应用补丁
val patchCli = tasks.register("patchCli") {
    group = "build"
    description = "使用 AST 转换应用补丁生成增强版 CLI"
    dependsOn(downloadCli, installPatchDeps)

    // 将路径捕获到局部变量，避免 configuration cache 问题
    val propsFilePath = file("cli-version.properties").absolutePath
    val bundledDirPath = file("src/main/resources/bundled").absolutePath
    val patchScriptPath = file("cli-patches/patch-cli.js").absolutePath
    val patchesDirPath = file("cli-patches/patches").absolutePath
    val cliPatchesDirPath = file("cli-patches").absolutePath

    inputs.file(propsFilePath)
    inputs.file(patchScriptPath)
    inputs.dir(patchesDirPath)

    // 动态计算输出文件路径
    val propsForOutput = Properties()
    if (File(propsFilePath).exists()) {
        File(propsFilePath).inputStream().use { propsForOutput.load(it) }
    }
    val cliVerForOutput = propsForOutput.getProperty("cli.version") ?: "unknown"
    val enhancedFilePath = "$bundledDirPath/claude-cli-$cliVerForOutput-enhanced.mjs"
    outputs.file(enhancedFilePath)

    // 仅当补丁脚本存在且增强文件不存在或需要更新时运行
    onlyIf {
        val patchExists = File(patchScriptPath).exists()
        val enhancedExists = File(enhancedFilePath).exists()
        patchExists && (!enhancedExists || !inputs.files.isEmpty)
    }

    doLast {
        val propsFile = File(propsFilePath)
        val bundledDirFile = File(bundledDirPath)
        val patchScript = File(patchScriptPath)
        val cliPatchesDir = File(cliPatchesDirPath)

        val props = Properties()
        propsFile.inputStream().use { props.load(it) }
        val cliVer = props.getProperty("cli.version") ?: error("cli.version missing")

        // 从 cli-patches 目录读取原始 CLI
        val cliJsFile = cliPatchesDir.resolve("claude-cli-$cliVer.js")
        // 输出增强版到 bundled 目录（使用 .mjs 扩展名确保 Node.js 识别为 ES Module）
        val enhancedFile = bundledDirFile.resolve("claude-cli-$cliVer-enhanced.mjs")

        if (!cliJsFile.exists()) {
            throw GradleException("CLI 文件不存在: ${cliJsFile.absolutePath}")
        }

        // 确保 bundled 目录存在
        bundledDirFile.mkdirs()

        println("========================================")
        println("使用 AST 转换应用补丁")
        println("========================================")

        val process = ProcessBuilder(
            "node",
            patchScript.absolutePath,
            cliJsFile.absolutePath,
            enhancedFile.absolutePath
        )
            .directory(cliPatchesDir)
            .redirectErrorStream(true)
            .start()

        // 实时输出
        process.inputStream.bufferedReader().forEachLine { line ->
            println(line)
        }

        val exitCode = process.waitFor()

        if (exitCode != 0) {
            throw GradleException("AST 补丁应用失败")
        }
    }
}

// 验证补丁
val verifyPatches = tasks.register("verifyPatches") {
    group = "verification"
    description = "验证补丁是否正确应用"
    dependsOn(patchCli)

    // 将路径捕获到局部变量，避免 configuration cache 问题
    val propsFilePath = file("cli-version.properties").absolutePath
    val bundledDirPath = file("src/main/resources/bundled").absolutePath

    doLast {
        val propsFile = File(propsFilePath)
        val bundledDirFile = File(bundledDirPath)

        val props = Properties()
        propsFile.inputStream().use { props.load(it) }
        val cliVer = props.getProperty("cli.version") ?: error("cli.version missing")

        val enhancedFile = bundledDirFile.resolve("claude-cli-$cliVer-enhanced.mjs")

        if (!enhancedFile.exists()) {
            throw GradleException("增强版 CLI 不存在")
        }

        val content = enhancedFile.readText()

        println("========================================")
        println("验证补丁应用")
        println("========================================")

        val checks = listOf(
            "run_to_background" to "统一后台控制命令",
            "agent_run_to_background" to "Agent后台控制命令",
            "get_chrome_status" to "Chrome状态查询",
            "mcp_tools" to "MCP工具列表查询",
            "get_capabilities" to "功能状态查询",
            "sourceToolUseID" to "Skill parent_tool_use_id"
        )

        var passed = 0
        var failed = 0

        for ((pattern, desc) in checks) {
            if (content.contains(pattern)) {
                println("✅ $desc: 已找到 '$pattern'")
                passed++
            } else {
                println("❌ $desc: 未找到 '$pattern'")
                failed++
            }
        }

        println()
        println("验证结果: $passed 通过, $failed 失败")
        println("========================================")

        if (failed > 0) {
            throw GradleException("补丁验证失败")
        }
    }
}

// 复制增强 CLI 到 VS Code 扩展目录
val copyToVsCodeExtension = tasks.register("copyToVsCodeExtension") {
    group = "build"
    description = "复制增强版 CLI 到 VS Code 扩展的 resources 目录"
    dependsOn(patchCli)

    // 将路径捕获到局部变量
    val propsFilePath = file("cli-version.properties").absolutePath
    val bundledDirPath = file("src/main/resources/bundled").absolutePath
    val vscodeBundledPath = rootProject.file("vscode-extension/resources/bundled").absolutePath
    val vscodePatchesPath = rootProject.file("vscode-extension/resources/cli-patches").absolutePath
    val patchesDirPath = file("cli-patches/patches").absolutePath

    doLast {
        val propsFile = File(propsFilePath)
        val bundledDir = File(bundledDirPath)
        val vscodeBundledDir = File(vscodeBundledPath)
        val vscodePatchesDir = File(vscodePatchesPath)
        val patchesDir = File(patchesDirPath)

        // 确保目标目录存在
        vscodeBundledDir.mkdirs()
        vscodePatchesDir.mkdirs()

        val props = Properties()
        propsFile.inputStream().use { props.load(it) }
        val cliVer = props.getProperty("cli.version") ?: error("cli.version missing")

        val enhancedFile = bundledDir.resolve("claude-cli-$cliVer-enhanced.mjs")
        val targetFile = vscodeBundledDir.resolve("claude-cli-enhanced.mjs")

        if (enhancedFile.exists()) {
            enhancedFile.copyTo(targetFile, overwrite = true)
            println("✅ 已复制增强 CLI 到: ${targetFile.absolutePath}")
            println("   大小: ${targetFile.length() / 1024 / 1024} MB")
        } else {
            println("⚠️ 增强 CLI 不存在: ${enhancedFile.absolutePath}")
        }

        // 复制补丁文件（仅供参考/调试）
        var patchCount = 0
        patchesDir.listFiles()?.filter { it.extension == "js" }?.forEach { patch ->
            patch.copyTo(vscodePatchesDir.resolve(patch.name), overwrite = true)
            patchCount++
        }
        println("✅ 已复制 $patchCount 个补丁文件到: ${vscodePatchesDir.absolutePath}")
    }
}

// patchCli 完成后自动复制到 VS Code 扩展
patchCli.configure {
    finalizedBy(copyToVsCodeExtension)
}

// 修改 processResources 依赖
tasks.named("processResources") {
    dependsOn(downloadCli, copyCliVersionProps, patchCli)
}

// sourcesJar 任务也需要依赖 patchCli（避免任务顺序问题）
tasks.named("sourcesJar") {
    dependsOn(downloadCli, copyCliVersionProps, patchCli)
}
