import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.1.20" apply false  // 与IDE平台保持一致
    kotlin("plugin.serialization") version "2.1.20" apply false

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






