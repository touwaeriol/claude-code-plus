pluginManagement {
    repositories {
        // JetBrains 仓库优先 (专有仓库无镜像)
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        
        // 阿里云镜像 (最稳定、速度快)
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/gradle-plugin") 
        maven("https://maven.aliyun.com/repository/central")
        
        // 回退到官方仓库
        google()
        gradlePluginPortal()
        mavenCentral()
        maven("https://plugins.gradle.org/m2/")
    }
}

dependencyResolutionManagement {
    repositories {
        // 暂时使用官方仓库确保构建成功
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        google()
        mavenCentral()
        
        // 阿里云镜像作为备选
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
    }
}

rootProject.name = "claude-code-plus"

// 子模块配置
include(
    "toolwindow",
    "jetbrains-plugin",
    "claude-code-sdk",
    "standalone-test"
)
