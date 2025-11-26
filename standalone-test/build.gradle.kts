plugins {
    kotlin("jvm")
    application
}



dependencies {
    implementation(project(":claude-agent-sdk"))
    implementation(project(":ai-agent-sdk"))
    implementation(project(":ai-agent-server"))
    implementation(project(":ai-agent-rpc-api"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}

val mainOverride = project.findProperty("mainClass") as String?
application {
    mainClass.set(mainOverride ?: "standalone.TestModelSwitchKt")
}

tasks.named<JavaExec>("run") {
    environment("CLAUDE_API_KEY", System.getenv("CLAUDE_API_KEY") ?: "")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}