plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij.platform")
}

group = "com.claudecodeplus"
version = "1.0.1"

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
        // 排除 toolwindow 模块的 Jewel 依赖，避免类加载器冲突
        exclude(group = "org.jetbrains.jewel")
        // 排除 Compose 依赖，使用插件环境的版本
        exclude(group = "org.jetbrains.compose.ui")
        exclude(group = "org.jetbrains.compose.foundation")
        exclude(group = "org.jetbrains.compose.runtime")
        exclude(group = "org.jetbrains.compose.animation")
    }
    
    // Jewel IDE Bridge - 用于 IntelliJ 插件环境
    val jewelVersion = rootProject.extra["jewelVersion"] as String
    implementation("org.jetbrains.jewel:jewel-foundation:$jewelVersion")
    implementation("org.jetbrains.jewel:jewel-ui:$jewelVersion")
    // 添加 standalone theme 支持，运行时需要
    implementation("org.jetbrains.jewel:jewel-int-ui-standalone:$jewelVersion")
    // 暂时移除 IDE Bridge，因为它导致了 ToolWindowFactory 的类加载器冲突
    // 图标问题已通过 IconUtils.kt 的 fallback 机制解决
    // implementation("org.jetbrains.jewel:jewel-ide-laf-bridge:0.28.0-251.25410.129")
    
    // IntelliJ Platform dependencies
    intellijPlatform {
        // 使用较新的版本以确保对 252.* 的兼容性
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
            "-Xmx2048m"
        )
    }
    
    buildSearchableOptions {
        enabled = false
    }
}