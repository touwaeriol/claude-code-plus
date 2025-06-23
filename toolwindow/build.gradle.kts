import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    `java-library`  // 添加 java-library 插件以支持 api 配置
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
    // 依赖 cli-wrapper 模块
    implementation(project(":cli-wrapper"))
    
    // Kotlin 标准库
    implementation(kotlin("stdlib"))
    
    // Compose Desktop - 使用 api 传递依赖
    api(compose.desktop.currentOs)
    api(compose.runtime)
    api(compose.foundation)
    api(compose.animation)
    api(compose.ui)
    api(compose.material) // 需要 Material 组件用于 DropdownMenu
    
    // Jewel UI - 使用 api 传递依赖
    val jewelVersion = "0.28.0-251.26137"  // 使用与 IntelliJ 251 兼容的版本
    
    // 从 Maven Central 引入 Jewel
    api("org.jetbrains.jewel:jewel-foundation:$jewelVersion")
    api("org.jetbrains.jewel:jewel-ui:$jewelVersion")
    api("org.jetbrains.jewel:jewel-int-ui-standalone:$jewelVersion")

    // Kotlin 协程
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.7.3")
    
    // Markdown 解析
    implementation("org.commonmark:commonmark:0.21.0")
    
    // 测试依赖
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("io.mockk:mockk:1.13.8")
}

compose.desktop {
    application {
        mainClass = "MainKt"
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
}