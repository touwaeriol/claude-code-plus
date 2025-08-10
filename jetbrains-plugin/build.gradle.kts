plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij.platform")
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
    // 依赖其他模块 - 按照官方方式排除整个 kotlinx 组
    implementation(project(":cli-wrapper")) {
        exclude(group = "org.jetbrains.kotlinx")
    }
    implementation(project(":toolwindow")) {
        exclude(group = "org.jetbrains.kotlinx")
        // 排除 Compose Material，使用 Jewel
        exclude(group = "org.jetbrains.compose.material")
    }
    
    // IntelliJ Platform dependencies
    intellijPlatform {
        intellijIdeaCommunity("2025.1.4.1")
        
        // 添加 Markdown 插件依赖
        bundledPlugin("org.intellij.plugins.markdown")
        
        // 添加 Git 插件依赖
        bundledPlugin("Git4Idea")
        
        // 添加 Java 插件依赖（用于 PSI 类）
        bundledPlugin("com.intellij.java")
    }
    
    // Compose 运行时由 IntelliJ 平台提供，不需要显式添加
    
    // 使用 IntelliJ Platform 的 Kotlin 标准库
    compileOnly(kotlin("stdlib"))
    
    // 测试依赖
    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.13.8")
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
    runIde {
        jvmArgs(
            "-Xmx2048m"
        )
    }
    
    buildSearchableOptions {
        enabled = false
    }
}