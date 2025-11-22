plugins {
    kotlin("jvm")
    application
}



dependencies {
    implementation(project(":claude-code-sdk"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}

application {
    mainClass.set("standalone.TestModelSwitchKt")
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