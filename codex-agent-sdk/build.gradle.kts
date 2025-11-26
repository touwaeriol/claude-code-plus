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

println("🔍 [Codex SDK] version = $version")
println("🔍 [Codex SDK] version.toString() = ${version.toString()}")
println("🔍 [Codex SDK] isSnapshotRelease = $isSnapshotRelease")

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
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
        create<MavenPublication>("codexSdkKotlin") {
            from(components["java"])
            artifactId = "codex-agent-sdk-kotlin"
            configureCommonPom(
                displayName = "Codex Agent SDK (Kotlin)",
                moduleDescription = "Kotlin bindings for the Codex Agent SDK."
            )
        }
        create<MavenPublication>("codexSdkJava") {
            from(components["java"])
            artifactId = "codex-agent-sdk-java"
            configureCommonPom(
                displayName = "Codex Agent SDK (Java)",
                moduleDescription = "Java packaging of the Codex Agent SDK bytecode for pure-Java projects."
            )
        }
    }
    repositories {
        maven {
            name = "sonatype"
            // 强制使用 staging URL（版本 0.1.0 是 release，不是 SNAPSHOT）
            url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
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


