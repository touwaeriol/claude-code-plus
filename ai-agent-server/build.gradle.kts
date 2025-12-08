import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile


plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("application") // For running as a standalone app
    `java-library` // For api() dependency configuration
}

group = "com.asakii"
version = "1.0.5" // Align with the main plugin version



dependencies {
    // Project dependencies
    implementation(project(":ai-agent-sdk"))
    api(project(":ai-agent-rpc-api")) // Use api to expose types to downstream
    implementation(project(":ai-agent-proto")) // Protobuf 生成的类型

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

    // RSocket (over WebSocket) - 0.20.0 for Ktor 3.x compatibility
    val rsocketVersion = "0.20.0"
    implementation("io.rsocket.kotlin:rsocket-core:$rsocketVersion")
    implementation("io.rsocket.kotlin:ktor-server-rsocket:$rsocketVersion")

    // Logging
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    implementation("ch.qos.logback:logback-classic:1.4.14")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.13.8")
}

application {
    mainClass.set("com.asakii.server.StandaloneServerKt")
    
    // 设置 JVM 参数，确保使用 UTF-8 编码
    applicationDefaultJvmArgs = listOf(
        "-Dfile.encoding=UTF-8",
        "-Dconsole.encoding=UTF-8",
        "-Duser.language=en",
        "-Duser.country=US"
    )
}

kotlin {
    jvmToolchain(17)
}

// 确保编译前先生成 proto
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    dependsOn(":ai-agent-proto:generateProto")
}





