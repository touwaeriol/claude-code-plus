import com.google.protobuf.gradle.*

plugins {
    kotlin("jvm")
    id("com.google.protobuf") version "0.9.4"
    `maven-publish`
}

group = "com.asakii"
version = "1.0-SNAPSHOT"

val protobufVersion = "3.25.3"

dependencies {
    // Protobuf 运行时
    api("com.google.protobuf:protobuf-kotlin:$protobufVersion")
    api("com.google.protobuf:protobuf-java-util:$protobufVersion")

    // Kotlin 协程（用于未来的 gRPC 支持）
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${rootProject.extra["coroutinesVersion"]}")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protobufVersion"
    }

    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                // 生成 Kotlin DSL 扩展（java 是默认的，不需要显式添加）
                create("kotlin")
            }
        }
    }
}

// 确保 proto 生成的代码被正确包含在源码集中
sourceSets {
    main {
        proto {
            srcDir("src/main/proto")
        }
        java {
            srcDirs(
                "build/generated/source/proto/main/java",
                "build/generated/source/proto/main/kotlin"
            )
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    dependsOn("generateProto")
}

// 处理资源文件重复问题
tasks.withType<ProcessResources> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// 统一 JVM 版本为 17（与其他模块保持一致）
kotlin {
    jvmToolchain(17)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["kotlin"])
            artifactId = "ai-agent-proto"
        }
    }
}
