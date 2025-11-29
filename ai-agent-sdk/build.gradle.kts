plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("java-library")
}

group = "com.asakii"
version = "0.1.0"

kotlin {
    jvmToolchain(17)
}

java {
    withSourcesJar()
}

dependencies {
    api("org.jetbrains.kotlin:kotlin-stdlib")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    api(project(":claude-agent-sdk"))
    api(project(":codex-agent-sdk"))

    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}

tasks.test {
    useJUnitPlatform()
}






























