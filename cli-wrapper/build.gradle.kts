plugins {
    kotlin("jvm")
}

group = "com.claudecodeplus"
version = "1.0-SNAPSHOT"

dependencies {
    // JSON 处理
    implementation("org.json:json:20231013")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.3")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.3")
    
    // 文件监听和IO优化
    implementation("commons-io:commons-io:${rootProject.extra["commonsIoVersion"]}")
    
    // 高性能缓存
    implementation("com.github.ben-manes.caffeine:caffeine:${rootProject.extra["caffeineVersion"]}")
    
    // ANSI 处理
    implementation("org.fusesource.jansi:jansi:2.4.1")
    
    // 测试依赖
    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.13.8")
}