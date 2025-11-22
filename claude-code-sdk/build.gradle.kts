plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
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
    implementation(project(":claude-code-rpc-api"))

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

// 运行示例的任务
tasks.register<JavaExec>("runModelTest") {
    group = "verification"


    description = "运行模型切换测试示例"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.claudecodeplus.sdk.examples.ModelIdentificationTestKt")
    standardInput = System.`in`
}

tasks.register<JavaExec>("runSonnet45Test") {
    group = "verification"
    description = "测试切换到 Sonnet 4.5"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.claudecodeplus.sdk.examples.SwitchToSonnet45TestKt")
    standardInput = System.`in`
}

tasks.register<JavaExec>("runSlashCommandTest") {
    group = "verification"
    description = "测试 /model 斜杠命令"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.claudecodeplus.sdk.examples.SlashCommandModelTestKt")
    standardInput = System.`in`
}

tasks.register<JavaExec>("runOpusTest") {
    group = "verification"
    description = "测试切换到 Opus 模型"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.claudecodeplus.sdk.examples.OpusSwitchTestKt")
    standardInput = System.`in`
}

tasks.register<JavaExec>("runToolTest") {
    group = "verification"
    description = "测试工具调用解析和显示"
    classpath = sourceSets["test"].runtimeClasspath + sourceSets["main"].runtimeClasspath
    mainClass.set("com.claudecodeplus.sdk.test.TestClaudeToolsKt")
    standardInput = System.`in`
}

tasks.register<JavaExec>("testInputSerialization") {
    group = "verification"
    description = "测试 SpecificToolUse input 字段序列化"
    classpath = sourceSets["test"].runtimeClasspath + sourceSets["main"].runtimeClasspath
    mainClass.set("com.claudecodeplus.sdk.test.TestInputSerializationKt")
    standardInput = System.`in`
}

tasks.register<JavaExec>("runJointTestClient") {
    group = "verification"
    description = "Runs the joint test client to connect to a running server"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.claudecodeplus.sdk.examples.JointTestClientKt")
}


