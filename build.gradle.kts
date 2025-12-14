import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.1.20" apply false  // 与IDE平台保持一致
    kotlin("plugin.serialization") version "2.1.20" apply false
    id("org.jetbrains.dokka") version "1.9.20" apply false
    id("io.github.gradle-nexus.publish-plugin") version "2.0.0"
}


group = "com.asakii"
version = "1.0.8"

// 集中管理依赖版本 - 与IntelliJ Platform 2025.1内置版本对齐
extra["kotlinVersion"] = "2.1.20"  // 与IDE平台保持一致
extra["coroutinesVersion"] = "1.7.3"
extra["serializationVersion"] = "1.8.0"  // 更新到IDE平台版本
extra["commonsIoVersion"] = "2.15.1"
extra["caffeineVersion"] = "3.1.8"
extra["rxKotlinVersion"] = "3.0.1"
extra["junitVersion"] = "5.10.1"

val ossrhUsername: String? by project
val ossrhPassword: String? by project

// 注释掉根项目的 nexusPublishing 配置，让子模块自己控制
// nexusPublishing {
//     repositories {
//         sonatype {
//             nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
//             snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
//             username.set(ossrhUsername ?: System.getenv("OSSRH_USERNAME"))
//             password.set(ossrhPassword ?: System.getenv("OSSRH_PASSWORD"))
//         }
//     }
// }

