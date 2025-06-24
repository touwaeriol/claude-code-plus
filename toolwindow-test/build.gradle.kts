import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose") version "1.8.0-alpha04"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.10"
}

group = "com.claudecodeplus"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    google()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    maven("https://packages.jetbrains.team/maven/p/kpm/public")
    maven("https://www.jetbrains.com/intellij-repository/releases")
    maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
}

dependencies {
    // 依赖其他模块
    implementation(project(":cli-wrapper"))
    implementation(project(":toolwindow"))  // toolwindow 通过 api 传递了 Compose 和 Jewel 依赖

    // Kotlin 标准库
    implementation(kotlin("stdlib"))

    // Kotlin 协程
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    
    // Compose Desktop
    implementation(compose.desktop.currentOs)
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.animation)
    implementation(compose.ui)
    implementation(compose.material)
    
    // Jewel UI - 显式添加以确保类路径正确
    val jewelVersion = "0.28.0-251.26137"
    implementation("org.jetbrains.jewel:jewel-foundation:$jewelVersion")
    implementation("org.jetbrains.jewel:jewel-ui:$jewelVersion")
    implementation("org.jetbrains.jewel:jewel-int-ui-standalone:$jewelVersion")

    // 测试依赖
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
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

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }

    withType<KotlinCompile> {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    test {
        useJUnitPlatform()
    }

    
    // 创建运行简单 Compose 测试的任务
    register<JavaExec>("runSimpleComposeApp") {
        group = "verification"
        description = "Run the simple Compose test application"

        dependsOn("classes")
        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("com.claudecodeplus.test.SimpleComposeAppKt")

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
            "-Djava.awt.headless=false"
        )

        // 确保能看到输出
        standardOutput = System.out
        errorOutput = System.err
    }
    
    // 创建运行 Compose 版本的任务
    register<JavaExec>("runComposeJewelTestApp") {
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



}
