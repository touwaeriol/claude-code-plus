import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Jar
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.dokka.gradle.DokkaTask

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("java-library")
    id("maven-publish")
    id("signing")
    id("org.jetbrains.dokka")
}

group = "com.asakii"
version = "0.1.0"

val ossrhUsername: String? by project
val ossrhPassword: String? by project
val isSnapshotRelease = version.toString().endsWith("SNAPSHOT")

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    // Kotlin 标准库和协程
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // 统一日志模块 (SLF4J)
    api(project(":unified-logging"))

    // JSON 序列化
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    implementation("org.jetbrains.kotlinx:kotlinx-rpc-krpc-ktor-client:0.10.1") // kRPC over Ktor client



    // Hutool 缓存和加密工具
    implementation("cn.hutool:hutool-cache:5.8.25")
    implementation("cn.hutool:hutool-crypto:5.8.25")

}

kotlin {
    jvmToolchain(17)
}

// 🔧 配置 Dokka 任务以支持配置缓存
tasks.named<Jar>("javadocJar") {
    val dokkaJavadoc = tasks.named<DokkaTask>("dokkaJavadoc")
    dependsOn(dokkaJavadoc)
    // 使用 Provider API 延迟解析,避免配置缓存问题
    from(dokkaJavadoc.map { it.outputDirectory })
}

fun MavenPublication.configureCommonPom(displayName: String, moduleDescription: String) {
    pom {
        name.set(displayName)
        description.set(moduleDescription)
        url.set("https://github.com/asakii/claude-code-plus")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
            }
        }
        developers {
            developer {
                id.set("asakii")
                name.set("Asakii Team")
                email.set("opensource@asakii.com")
            }
        }
        scm {
            connection.set("scm:git:https://github.com/asakii/claude-code-plus.git")
            developerConnection.set("scm:git:ssh://git@github.com:asakii/claude-code-plus.git")
            url.set("https://github.com/asakii/claude-code-plus")
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("claudeAgentSdkKotlin") {
            from(components["java"])
            artifactId = "claude-agent-sdk-kotlin"
            configureCommonPom(
                displayName = "Claude Agent SDK (Kotlin)",
                moduleDescription = "Kotlin-first SDK for integrating Claude Agents."
            )
        }
        create<MavenPublication>("claudeAgentSdkJava") {
            from(components["java"])
            artifactId = "claude-agent-sdk-java"
            configureCommonPom(
                displayName = "Claude Agent SDK (Java)",
                moduleDescription = "Java-friendly distribution of the Claude Agent SDK with the same JVM bytecode."
            )
        }
    }
    repositories {
        maven {
            name = "sonatype"
            url = uri(
                if (isSnapshotRelease) {
                    "https://s01.oss.sonatype.org/content/repositories/snapshots/"
                } else {
                    "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
                }
            )
            credentials {
                username = ossrhUsername ?: System.getenv("OSSRH_USERNAME")
                password = ossrhPassword ?: System.getenv("OSSRH_PASSWORD")
            }
        }
    }
}

signing {
    useGpgCmd()
    isRequired = !version.toString().endsWith("SNAPSHOT")
    sign(publishing.publications)
}
// 运行示例的任务
tasks.register<JavaExec>("runModelTest") {
    group = "verification"


    description = "运行模型切换测试示例"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.asakii.claude.agent.sdk.examples.ModelIdentificationTestKt")
    standardInput = System.`in`
}

tasks.register<JavaExec>("runSonnet45Test") {
    group = "verification"
    description = "测试切换到 Sonnet 4.5"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.asakii.claude.agent.sdk.examples.SwitchToSonnet45TestKt")
    standardInput = System.`in`
}

tasks.register<JavaExec>("runSlashCommandTest") {
    group = "verification"
    description = "测试 /model 斜杠命令"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.asakii.claude.agent.sdk.examples.SlashCommandModelTestKt")
    standardInput = System.`in`
}

tasks.register<JavaExec>("runOpusTest") {
    group = "verification"
    description = "测试切换到 Opus 模型"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.asakii.claude.agent.sdk.examples.OpusSwitchTestKt")
    standardInput = System.`in`
}

tasks.register<JavaExec>("runJointTestClient") {
    group = "verification"
    description = "Runs the joint test client to connect to a running server"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.asakii.claude.agent.sdk.examples.JointTestClientKt")
}

tasks.register<JavaExec>("runQuickConnectionTest") {
    group = "verification"
    description = "快速测试 SDK 连接"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.asakii.claude.agent.sdk.examples.QuickConnectionTestKt")
    standardInput = System.`in`
}

// CLI 说明：不再捆绑 Claude CLI MJS 文件，改为使用系统安装的 claude 命令
// 用户需要自行安装：npm install -g @anthropic-ai/claude-code
