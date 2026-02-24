import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform


pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }

    // Dynamically resolve Kotlin plugin version from gradle property
    // This allows buildAllVersions to pass different Kotlin versions per platform
    val kotlinVersion: String by settings
    plugins {
        kotlin("jvm") version kotlinVersion
        kotlin("plugin.serialization") version kotlinVersion
    }
}

plugins {
    id("org.jetbrains.intellij.platform.settings") version "2.11.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()
        intellijPlatform {
            defaultRepositories()
        }
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://packages.jetbrains.team/maven/p/kmp/public")
    }


}

rootProject.name = "claude-code-plus"

// 子模块配置
include(
    "toolwindow",
    "jetbrains-plugin",
    "claude-agent-sdk",
    "codex-agent-sdk",
    "ai-agent-sdk",
    "ai-agent-rpc-api",
    "ai-agent-proto",  // RSocket + Protobuf 通信模块
    "ai-agent-server",
    "unified-logging"  // 统一日志模块 (SLF4J)
)
