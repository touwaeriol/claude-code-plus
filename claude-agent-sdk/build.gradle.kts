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

    // JSON 序列化
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    implementation("org.jetbrains.kotlinx:kotlinx-rpc-krpc-ktor-client:0.10.1") // kRPC over Ktor client

    // 测试依赖
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    implementation(project(":ai-agent-rpc-api"))

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("io.mockk:mockk:1.13.8")
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}

val dokkaJavadoc by tasks.getting(DokkaTask::class)

tasks.named<Jar>("javadocJar") {
    dependsOn(dokkaJavadoc)
    from(dokkaJavadoc.outputDirectory)
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

tasks.register<JavaExec>("runToolTest") {
    group = "verification"
    description = "测试工具调用解析和显示"
    classpath = sourceSets["test"].runtimeClasspath + sourceSets["main"].runtimeClasspath
    mainClass.set("com.asakii.claude.agent.sdk.test.TestClaudeToolsKt")
    standardInput = System.`in`
}

tasks.register<JavaExec>("testInputSerialization") {
    group = "verification"
    description = "测试 SpecificToolUse input 字段序列化"
    classpath = sourceSets["test"].runtimeClasspath + sourceSets["main"].runtimeClasspath
    mainClass.set("com.asakii.claude.agent.sdk.test.TestInputSerializationKt")
    standardInput = System.`in`
}

tasks.register<JavaExec>("runJointTestClient") {
    group = "verification"
    description = "Runs the joint test client to connect to a running server"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.asakii.claude.agent.sdk.examples.JointTestClientKt")
}


