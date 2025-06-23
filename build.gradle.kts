import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.1.10" apply false
    id("org.jetbrains.intellij.platform") version "2.6.0" apply false
    id("org.jetbrains.compose") version "1.8.0-alpha04" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.10" apply false
}

group = "com.claudecodeplus"
version = "1.0-SNAPSHOT"

// 配置所有子项目
subprojects {
    apply(plugin = "kotlin")
    
    repositories {
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://packages.jetbrains.team/maven/p/kpm/public")
        maven("https://www.jetbrains.com/intellij-repository/releases")
        maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
    }
    
    // 通用的 Kotlin 编译配置
    tasks.withType<KotlinCompile> {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    
    // 通用的 Java 编译配置
    tasks.withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
}