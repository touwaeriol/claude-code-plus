import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

group = "com.claudecodeplus"
version = "1.0-SNAPSHOT"

dependencies {
    // 依赖其他模块
    implementation(project(":cli-wrapper"))
    implementation(project(":toolwindow"))  // toolwindow 通过 api 传递了 Compose 和 Jewel 依赖

    // 测试依赖
    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.13.8")
}

compose.desktop {
    application {
        mainClass = "com.claudecodeplus.test.JewelChatTestAppKt"

        buildTypes.release.proguard {
            isEnabled.set(false)
        }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "ComposeJewelTestApp"
            packageVersion = "1.0.0"

            macOS {
                // macOS 特定配置
                bundleID = "com.claudecodeplus.test"
            }
        }
    }
}

// 创建运行简单 Compose 应用的任务
tasks.register<JavaExec>("runSimpleComposeApp") {
    group = "verification"
    description = "Run the simple Compose test application"
    
    dependsOn("classes")
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.claudecodeplus.test.SimpleComposeTestKt")
    
    // 设置工作目录为 claude-code-plus 项目根目录
    workingDir = file(projectDir.parent)
    
    // 通过系统属性传递项目根目录路径
    systemProperty("project.root", projectDir.parent)
    
    // JVM 参数
    jvmArgs = listOf(
        "-Xmx512m",
        "--add-opens", "java.desktop/sun.awt=ALL-UNNAMED",
        "--add-opens", "java.desktop/java.awt.event=ALL-UNNAMED",
        "-Dproject.root=${projectDir.parent}"
    )
    
    // 确保能看到输出
    standardOutput = System.out
    errorOutput = System.err
}

// 创建运行 Compose 版本的任务
tasks.register<JavaExec>("runComposeJewelTestApp") {
    group = "verification"
    description = "Run the Compose Jewel test application"

    dependsOn("classes")
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.claudecodeplus.test.JewelChatTestAppKt")

    // 设置工作目录为 claude-code-plus 项目根目录
    workingDir = file(projectDir.parent)

    // 通过系统属性传递项目根目录路径
    systemProperty("project.root", projectDir.parent)

    // JVM 参数
    jvmArgs = listOf(
        "-Xmx1024m",
        "--add-opens", "java.desktop/sun.awt=ALL-UNNAMED",
        "--add-opens", "java.desktop/java.awt.event=ALL-UNNAMED",
        "--add-opens", "java.desktop/sun.font=ALL-UNNAMED",
        "--add-opens", "java.desktop/java.awt=ALL-UNNAMED",
        "--add-opens", "java.desktop/sun.java2d=ALL-UNNAMED",
        "-Dproject.root=${projectDir.parent}",
        "-Djava.awt.headless=false",
        "-Dskiko.library.path=${System.getProperty("java.io.tmpdir")}"
    )

    // 确保能看到输出
    standardOutput = System.out
    errorOutput = System.err
}