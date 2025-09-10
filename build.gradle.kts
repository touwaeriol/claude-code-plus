import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.1.20" apply false  // 与IDE平台保持一致
    kotlin("plugin.serialization") version "2.1.20" apply false
    id("org.jetbrains.intellij.platform") version "2.6.0" apply false
    // 🔄 临时恢复Compose插件以解决内联编译问题
    id("org.jetbrains.compose") version "1.7.3" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20" apply false
}

group = "com.claudecodeplus"
version = "1.0-SNAPSHOT"

// 集中管理依赖版本 - 与IntelliJ Platform 2025.1内置版本对齐
extra["kotlinVersion"] = "2.1.20"  // 与IDE平台保持一致
extra["coroutinesVersion"] = "1.7.3"
extra["serializationVersion"] = "1.8.0"  // 更新到IDE平台版本
// 🔄 临时恢复外部依赖版本以解决内联编译问题
extra["jewelVersion"] = "0.29.1"  // 使用已知存在的版本
extra["composeVersion"] = "1.7.3"  // 临时恢复
extra["commonsIoVersion"] = "2.15.1"
extra["caffeineVersion"] = "3.1.8"
extra["rxKotlinVersion"] = "3.0.1"
extra["junitVersion"] = "5.10.1"

// 配置所有子项目
subprojects {
    apply(plugin = "kotlin")
    
    repositories {
        // JetBrains 专有仓库优先
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://packages.jetbrains.team/maven/p/kmp/public") 
        maven("https://www.jetbrains.com/intellij-repository/releases")
        maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
        
        // 官方仓库
        mavenCentral()
        google()
        
        // 阿里云镜像作为备选
        maven("https://maven.aliyun.com/repository/central")
        maven("https://maven.aliyun.com/repository/google")
    }
    
    // 🔧 对于插件模块，只排除运行时的 kotlinx-coroutines，保留编译时
    if (project.name == "jetbrains-plugin") {
        configurations {
            // 只排除运行时配置，保留编译时配置
            named("runtimeClasspath") {
                exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
                exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core-jvm")
                exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-swing")
                exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-debug")
                exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-test")
                exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-jdk8")
            }
        }
    }
    
    // 通用依赖配置（排除会冲突的模块，让它们自己管理依赖）
    if (project.name != "jetbrains-plugin" && project.name != "cli-wrapper" && project.name != "toolwindow") {
        dependencies {
            val implementation by configurations
            val testImplementation by configurations
            val testRuntimeOnly by configurations
            
            // Kotlin 标准库
            implementation("org.jetbrains.kotlin:kotlin-stdlib:${rootProject.extra["kotlinVersion"]}")
            
            // 🚫 移除协程库的通用配置，避免意外引入冲突
            // 每个模块根据自己的需求配置协程依赖
            
            // 测试依赖
            testImplementation("org.junit.jupiter:junit-jupiter-api:${rootProject.extra["junitVersion"]}")
            testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:${rootProject.extra["junitVersion"]}")
        }
    }
    
    // 通用的 Kotlin 编译配置
    tasks.withType<KotlinCompile> {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            freeCompilerArgs.add("-Xjvm-default=all")
        }
    }
    
    // 通用的 Java 编译配置
    tasks.withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
        options.encoding = "UTF-8"
    }
    
    // 测试配置
    tasks.withType<Test> {
        useJUnitPlatform()
        systemProperty("file.encoding", "UTF-8")
    }
    
    // 设置 JVM 运行时编码
    tasks.withType<JavaExec> {
        systemProperty("file.encoding", "UTF-8")
        jvmArgs("-Dfile.encoding=UTF-8")
    }
}