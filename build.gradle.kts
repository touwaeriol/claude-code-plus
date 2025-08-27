import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.1.10" apply false
    kotlin("plugin.serialization") version "2.1.10" apply false
    id("org.jetbrains.intellij.platform") version "2.6.0" apply false
    id("org.jetbrains.compose") version "1.7.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.10" apply false
}

group = "com.claudecodeplus"
version = "1.0-SNAPSHOT"

// 集中管理依赖版本
extra["kotlinVersion"] = "2.1.10"
extra["coroutinesVersion"] = "1.7.3"
extra["serializationVersion"] = "1.6.2"
extra["jewelVersion"] = "0.29.0-251.27828"
extra["composeVersion"] = "1.7.0"
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
    
    // 通用依赖配置（jetbrains-plugin 模块会特殊处理）
    if (project.name != "jetbrains-plugin" && project.name != "cli-wrapper" && project.name != "toolwindow") {
        dependencies {
            val implementation by configurations
            val testImplementation by configurations
            val testRuntimeOnly by configurations
            
            // Kotlin 标准库
            implementation("org.jetbrains.kotlin:kotlin-stdlib:${rootProject.extra["kotlinVersion"]}")
            
            // 协程库
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${rootProject.extra["coroutinesVersion"]}")
            
            // 测试依赖
            testImplementation("org.junit.jupiter:junit-jupiter-api:${rootProject.extra["junitVersion"]}")
            testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:${rootProject.extra["junitVersion"]}")
            testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${rootProject.extra["coroutinesVersion"]}")
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