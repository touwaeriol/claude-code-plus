plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij.platform")
}

group = "com.claudecodeplus"
version = "1.0.3"

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
        // 使用较新的版本以确保对 252.* 的兼容性
        intellijIdeaCommunity("2025.1.4.1")
        
        // 🎯 Jewel和Compose内置模块 - 官方推荐方式！
        bundledModule("intellij.platform.jewel.foundation")
        bundledModule("intellij.platform.jewel.ui")
        bundledModule("intellij.platform.jewel.ideLafBridge")
        bundledModule("intellij.libraries.compose.foundation.desktop")  // 唯一可用的Compose库
        bundledModule("intellij.libraries.skiko")  // Compose的原生渲染库
        
        // 添加 Markdown 插件依赖
        bundledPlugin("org.intellij.plugins.markdown")
        
        // 添加 Git 插件依赖
        bundledPlugin("Git4Idea")
        
        // 添加 Java 插件依赖（用于 PSI 类）
        bundledPlugin("com.intellij.java")
    }
    
    // 🔧 移除外部Compose依赖，避免与IDE内置版本的类加载器冲突
    // jetbrains-plugin模块只使用IDE内置的Compose版本
    
    // 使用 IntelliJ Platform 的 Kotlin 标准库
    compileOnly(kotlin("stdlib"))
    
    // 🔧 编译时需要协程 API，但运行时会被排除，使用 IntelliJ Platform 内置版本
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${rootProject.extra["coroutinesVersion"]}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:${rootProject.extra["coroutinesVersion"]}")
    
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
        
        // 插件描述和变更日志将从 plugin.xml 读取
        description = providers.fileContents(layout.projectDirectory.file("src/main/resources/META-INF/plugin.xml")).asText.map {
            it.substringAfter("<description><![CDATA[").substringBefore("]]></description>")
        }
        
        changeNotes = providers.fileContents(layout.projectDirectory.file("src/main/resources/META-INF/plugin.xml")).asText.map {
            it.substringAfter("<change-notes><![CDATA[").substringBefore("]]></change-notes>")
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

tasks {
    runIde {
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
}