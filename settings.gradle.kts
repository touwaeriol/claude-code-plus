import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform


pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

plugins {
    id("org.jetbrains.intellij.platform.settings") version "2.10.4"
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
    "claude-code-sdk",
    "claude-code-rpc-api",
    "standalone-test",
    "claude-code-server"
)
