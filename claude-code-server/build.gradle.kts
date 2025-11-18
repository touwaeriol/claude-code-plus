import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile


plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("application") // For running as a standalone app
}

group = "com.claudecodeplus"
version = "1.0.4" // Align with the main plugin version



dependencies {
    // Project dependencies
    implementation(project(":claude-code-sdk"))

    implementation(project(":claude-code-rpc-api"))

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${rootProject.extra["coroutinesVersion"]}")

    // kotlinx-rpc dependencies are temporarily removed to resolve build issues.

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${rootProject.extra["serializationVersion"]}")

    // Ktor Server
    val ktorVersion = "3.0.3" // Use the same version as in the plugin
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")  // 使用 Netty 引擎替代 CIO
    implementation("io.ktor:ktor-server-sse:$ktorVersion")
    implementation("io.ktor:ktor-server-websockets:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-cors:$ktorVersion")
    implementation("io.ktor:ktor-server-double-receive:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.14")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.13.8")
}

application {
    mainClass.set("com.claudecodeplus.server.StandaloneServerKt")
}

kotlin {
    jvmToolchain(17)
}





