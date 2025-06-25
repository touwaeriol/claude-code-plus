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