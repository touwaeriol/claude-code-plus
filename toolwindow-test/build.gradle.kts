import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose") version "1.7.1"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0"
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
    
    // 测试依赖
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("io.mockk:mockk:1.13.8")
}

compose.desktop {
    application {
        mainClass = "com.claudecodeplus.test.RedesignedToolwindowTestKt"
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
    
    // 创建运行独立测试应用的任务
    register<JavaExec>("runTestApp") {
        group = "verification"
        description = "Run the standalone test application for UI components"
        
        dependsOn("classes")
        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("com.claudecodeplus.test.StandaloneTestAppKt")
        
        // 传递项目路径作为参数
        args = listOf(projectDir.parent)
        
        // 设置工作目录
        workingDir = file(projectDir.parent)
        
        // JVM 参数
        jvmArgs = listOf("-Xmx512m")
        
        // 确保能看到输出
        standardOutput = System.out
        errorOutput = System.err
    }
    
    // 创建运行文件索引构建器的任务
    register<JavaExec>("runFileIndexBuilder") {
        group = "verification"
        description = "Run the file index builder tool"
        
        dependsOn("classes")
        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("com.claudecodeplus.test.FileIndexBuilder")
        
        // 传递项目路径作为参数
        args = listOf(projectDir.parent)
        
        // 设置工作目录
        workingDir = file(projectDir.parent)
        
        // JVM 参数
        jvmArgs = listOf("-Xmx256m")
        
        // 确保能看到输出
        standardOutput = System.out
        errorOutput = System.err
    }
    
    // 创建运行 CLI Wrapper 测试的任务
    register<JavaExec>("runCliWrapperTest") {
        group = "verification"
        description = "Run the CLI wrapper test"
        
        dependsOn("classes")
        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("com.claudecodeplus.test.TestCliWrapperSimpleKt")
        
        workingDir = file(projectDir.parent)
        
        standardOutput = System.out
        errorOutput = System.err
    }
    
    // 创建运行 Jewel UI 测试应用的任务
    register<JavaExec>("runJewelTestApp") {
        group = "verification"
        description = "Run the Jewel UI test application"
        
        dependsOn("classes")
        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("com.claudecodeplus.test.JewelTestAppKt")
        
        // 传递项目路径作为参数
        args = listOf(projectDir.parent)
        
        // 设置工作目录
        workingDir = file(projectDir.parent)
        
        // JVM 参数 - Compose 需要更多内存
        jvmArgs = listOf(
            "-Xmx1024m",
            "--add-opens", "java.desktop/sun.awt=ALL-UNNAMED",
            "--add-opens", "java.desktop/java.awt.event=ALL-UNNAMED"
        )
        
        // 确保能看到输出
        standardOutput = System.out
        errorOutput = System.err
    }
    
    // 创建运行简单 Compose 测试的任务
    register<JavaExec>("runSimpleComposeTest") {
        group = "verification"
        description = "Run the simple Compose test application"
        
        dependsOn("classes")
        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("com.claudecodeplus.test.SimpleComposeTestKt")
        
        // JVM 参数
        jvmArgs = listOf(
            "-Xmx512m",
            "--add-opens", "java.desktop/sun.awt=ALL-UNNAMED",
            "--add-opens", "java.desktop/java.awt.event=ALL-UNNAMED"
        )
        
        // 确保能看到输出
        standardOutput = System.out
        errorOutput = System.err
    }
    
    // 创建运行 Material UI 测试应用的任务
    register<JavaExec>("runMaterialTestApp") {
        group = "verification"
        description = "Run the Material UI test application"
        
        dependsOn("classes")
        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("com.claudecodeplus.test.MaterialTestAppKt")
        
        // 传递项目路径作为参数
        args = listOf(projectDir.parent)
        
        // 设置工作目录
        workingDir = file(projectDir.parent)
        
        // JVM 参数 - Compose 需要更多内存
        jvmArgs = listOf(
            "-Xmx1024m",
            "--add-opens", "java.desktop/sun.awt=ALL-UNNAMED",
            "--add-opens", "java.desktop/java.awt.event=ALL-UNNAMED"
        )
        
        // 确保能看到输出
        standardOutput = System.out
        errorOutput = System.err
    }
    
    // 创建运行 Jewel 消息测试的任务
    register<JavaExec>("runJewelMessageTest") {
        group = "verification"
        description = "Run the Jewel message test application"
        
        dependsOn("classes")
        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("com.claudecodeplus.test.JewelMessageTestKt")
        
        // 设置工作目录
        workingDir = file(projectDir.parent)
        
        // JVM 参数
        jvmArgs = listOf(
            "-Xmx1024m",
            "--add-opens", "java.desktop/sun.awt=ALL-UNNAMED",
            "--add-opens", "java.desktop/java.awt.event=ALL-UNNAMED"
        )
        
        // 确保能看到输出
        standardOutput = System.out
        errorOutput = System.err
    }
}