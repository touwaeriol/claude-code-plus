plugins {
    kotlin("jvm")
    application
}

repositories {
    mavenCentral()
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

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}