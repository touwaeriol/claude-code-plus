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

    // 协程
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:${rootProject.extra["coroutinesVersion"]}")
    
    // 测试依赖
    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.13.8")
}

compose.desktop {
    application {
        mainClass = "MainKt"

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