import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

group = "com.claudecodeplus"
version = "1.0-SNAPSHOT"

dependencies {
    // 项目依赖
    implementation(project(":toolwindow"))
    implementation(project(":cli-wrapper"))
    
    // Compose Desktop
    implementation(compose.desktop.currentOs)
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.animation)
    implementation(compose.ui)
    
    // Jewel UI - 直接引入
    val jewelVersion = rootProject.extra["jewelVersion"] as String
    implementation("org.jetbrains.jewel:jewel-foundation:$jewelVersion")
    implementation("org.jetbrains.jewel:jewel-ui:$jewelVersion")
    implementation("org.jetbrains.jewel:jewel-int-ui-standalone:$jewelVersion")
    implementation("org.jetbrains.jewel:jewel-int-ui-decorated-window:$jewelVersion")

    // 协程
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:${rootProject.extra["coroutinesVersion"]}")
    
    // 测试依赖
    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.13.8")
}

compose.desktop {
    application {
        mainClass = "com.claudecodeplus.desktop.MainKt"
        
        // 设置 JVM 参数，确保使用 UTF-8 编码
        jvmArgs(
            "-Dfile.encoding=UTF-8",
            "-Dsun.stdout.encoding=UTF-8", 
            "-Dsun.stderr.encoding=UTF-8",
            "-Dconsole.encoding=UTF-8"
        )

        buildTypes.release.proguard {
            isEnabled.set(false)
        }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "ClaudeCodePlus"
            packageVersion = "1.0.0"

            macOS {
                // macOS 特定配置
                bundleID = "com.claudecodeplus.desktop"
            }
        }
    }
}

// 添加CLI测试任务
tasks.register<JavaExec>("runSessionTest") {
    mainClass.set("com.claudecodeplus.desktop.TestSessionCLIKt")
    classpath = sourceSets["main"].runtimeClasspath
}

// 添加完整会话管理测试任务
tasks.register<JavaExec>("runFullSessionTest") {
    mainClass.set("com.claudecodeplus.desktop.TestFullSessionManagerKt")
    classpath = sourceSets["main"].runtimeClasspath
}

// 添加会话面板测试任务
tasks.register<JavaExec>("runSessionPanelTest") {
    mainClass.set("com.claudecodeplus.desktop.TestSessionPanelOnlyKt")
    classpath = sourceSets["main"].runtimeClasspath
}