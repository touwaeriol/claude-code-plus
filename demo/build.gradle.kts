plugins {
    kotlin("jvm") version "1.9.22"
    application
}

repositories {
    mavenCentral()
}

application {
    mainClass.set("SimpleDemoKt")
}

tasks.register("test-inline-reference") {
    dependsOn("run")
    description = "测试内联引用功能"
}