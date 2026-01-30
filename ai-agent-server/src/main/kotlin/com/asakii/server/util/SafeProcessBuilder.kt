package com.asakii.server.util

import java.io.File
import java.nio.file.Path

/**
 * 进程执行安全工具
 *
 * 提供命令验证和进程执行的安全封装，防止命令注入攻击。
 *
 * 安全措施：
 * 1. 二进制白名单验证
 * 2. 危险模式检测（命令注入、shell 转义）
 * 3. 参数消毒
 */
object SafeProcessBuilder {

    /**
     * 允许执行的二进制文件白名单
     * 只有在此列表中的程序才能被执行
     */
    private val ALLOWED_BINARIES = setOf(
        // Node.js 生态
        "node", "npm", "npx", "pnpm", "yarn", "bun",
        // 版本控制
        "git", "gh",
        // 脚本语言
        "python", "python3", "pip", "pip3",
        // AI 工具
        "claude", "codex",
        // Shell（受限场景）
        "bash", "sh", "zsh",
        // Windows 特定
        "cmd", "cmd.exe", "powershell", "powershell.exe", "pwsh", "pwsh.exe",
        // 常用开发工具
        "java", "javac", "kotlin", "kotlinc", "gradle", "gradlew", "gradlew.bat",
        "mvn", "mvnw", "mvnw.cmd",
        "go", "cargo", "rustc",
        "dotnet", "nuget",
        // 构建工具
        "make", "cmake", "ninja",
        // 容器
        "docker", "docker-compose", "podman",
        // 测试工具
        "jest", "vitest", "pytest", "junit"
    )

    /**
     * 危险的命令模式（可能导致命令注入）
     */
    private val DANGEROUS_PATTERNS = listOf(
        Regex("""\$\([^)]+\)"""),           // $(command) - 命令替换
        Regex("""`[^`]+`"""),                // `command` - 反引号命令替换
        Regex(""";\s*\S"""),                 // ; command - 命令分隔符
        Regex("""\|\s*\S"""),                // | command - 管道
        Regex("""&&\s*\S"""),                // && command - 逻辑与
        Regex("""\|\|\s*\S"""),              // || command - 逻辑或
        Regex(""">\s*\S"""),                 // > file - 重定向（可能覆盖重要文件）
        Regex("""<\s*\S"""),                 // < file - 输入重定向
        Regex("""\$\{[^}]+\}"""),            // ${var} - 变量扩展
        Regex("""\$[A-Za-z_][A-Za-z0-9_]*""") // $VAR - 环境变量引用
    )

    /**
     * 验证结果
     */
    sealed class ValidationResult {
        object Success : ValidationResult()
        data class Failure(val reason: String) : ValidationResult()
    }

    /**
     * 验证命令是否安全
     *
     * @param command 要执行的命令列表
     * @param strictMode 严格模式下检查危险模式，非严格模式只检查白名单
     * @return 验证结果
     */
    fun validateCommand(
        command: List<String>,
        strictMode: Boolean = true
    ): ValidationResult {
        if (command.isEmpty()) {
            return ValidationResult.Failure("Empty command")
        }

        // 提取二进制名称（去除路径）
        val binaryPath = command[0]
        val binaryName = File(binaryPath).name.lowercase()
            .removeSuffix(".exe")
            .removeSuffix(".bat")
            .removeSuffix(".cmd")

        // 检查是否在白名单中
        if (!ALLOWED_BINARIES.any { binaryName == it || binaryName.startsWith("$it.") }) {
            return ValidationResult.Failure(
                "Binary not in whitelist: $binaryName. Allowed: ${ALLOWED_BINARIES.take(10).joinToString()}..."
            )
        }

        // 严格模式：检查参数中的危险模式
        if (strictMode) {
            command.drop(1).forEachIndexed { index, arg ->
                DANGEROUS_PATTERNS.forEach { pattern ->
                    if (pattern.containsMatchIn(arg)) {
                        return ValidationResult.Failure(
                            "Dangerous pattern detected in argument ${index + 1}: '$arg' matches pattern ${pattern.pattern}"
                        )
                    }
                }
            }
        }

        return ValidationResult.Success
    }

    /**
     * 创建安全的 ProcessBuilder
     *
     * @param command 命令列表
     * @param workingDirectory 工作目录（可选）
     * @param environment 额外的环境变量（可选）
     * @param strictMode 严格模式
     * @return 验证通过返回 ProcessBuilder，否则返回错误信息
     */
    fun createSafe(
        command: List<String>,
        workingDirectory: Path? = null,
        environment: Map<String, String> = emptyMap(),
        strictMode: Boolean = true
    ): Result<ProcessBuilder> {
        // 先验证命令
        when (val validation = validateCommand(command, strictMode)) {
            is ValidationResult.Success -> { /* 继续 */ }
            is ValidationResult.Failure -> {
                return Result.failure(SecurityException(validation.reason))
            }
        }

        // 创建 ProcessBuilder
        val processBuilder = ProcessBuilder(command)

        // 设置工作目录
        workingDirectory?.let {
            processBuilder.directory(it.toFile())
        }

        // 设置环境变量（只添加，不覆盖系统变量）
        if (environment.isNotEmpty()) {
            val env = processBuilder.environment()
            environment.forEach { (key, value) ->
                // 验证环境变量名
                if (isValidEnvVarName(key)) {
                    env[key] = value
                }
            }
        }

        return Result.success(processBuilder)
    }

    /**
     * 创建安全的 ProcessBuilder（便捷方法，使用可变参数）
     */
    fun createSafe(
        vararg command: String,
        workingDirectory: Path? = null,
        strictMode: Boolean = true
    ): Result<ProcessBuilder> {
        return createSafe(command.toList(), workingDirectory, emptyMap(), strictMode)
    }

    /**
     * 验证环境变量名是否合法
     */
    private fun isValidEnvVarName(name: String): Boolean {
        if (name.isBlank()) return false
        // 环境变量名只能包含字母、数字和下划线，且不能以数字开头
        return name.matches(Regex("^[A-Za-z_][A-Za-z0-9_]*$"))
    }

    /**
     * 消毒单个参数（移除或转义危险字符）
     *
     * 注意：这是一个辅助方法，不应该依赖它来保证安全。
     * 应该优先使用 validateCommand 来拒绝不安全的命令。
     */
    fun sanitizeArgument(arg: String): String {
        // 移除常见的 shell 特殊字符
        return arg
            .replace(Regex("""[`$;|&<>]"""), "")
            .replace(Regex("""\$\([^)]*\)"""), "")
            .replace(Regex("""\$\{[^}]*\}"""), "")
    }

    /**
     * 检查路径是否安全（防止路径遍历攻击）
     */
    fun isPathSafe(path: String, baseDirectory: Path): Boolean {
        if (path.isBlank()) return false

        // 检查路径遍历
        if (path.contains("..")) {
            // 规范化后检查是否仍在基目录下
            try {
                val normalizedPath = baseDirectory.resolve(path).normalize()
                return normalizedPath.startsWith(baseDirectory)
            } catch (e: Exception) {
                return false
            }
        }

        return true
    }

    /**
     * 扩展白名单（运行时动态添加）
     *
     * 注意：此方法应该谨慎使用，只在必要时调用。
     * 添加的二进制应该来自可信配置，而非用户输入。
     */
    fun extendAllowedBinaries(binaries: Set<String>): Set<String> {
        // 返回新的白名单（不修改原始集合）
        return ALLOWED_BINARIES + binaries.map { it.lowercase() }
    }
}
