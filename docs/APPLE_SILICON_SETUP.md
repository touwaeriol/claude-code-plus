# Apple Silicon Mac 配置指南

## 问题说明
在 Apple Silicon (M1/M2/M3) Mac 上运行插件时，可能会遇到使用 x64 架构版本的问题，这会导致：
- 性能降低（通过 Rosetta 2 转译）
- 内存使用增加
- 启动速度变慢

## 解决方案

### 1. 使用原生 ARM64 JDK
推荐使用支持 Apple Silicon 的 JDK：

#### 选项 A：Azul Zulu JDK (推荐)
```bash
# 使用 Homebrew 安装
brew install --cask zulu17

# 或者下载 ARM64 版本
# https://www.azul.com/downloads/?package=jdk#zulu
```

#### 选项 B：Amazon Corretto
```bash
# 使用 Homebrew 安装
brew install --cask corretto17
```

#### 选项 C：JetBrains Runtime (内置)
IntelliJ IDEA 自带的 JBR 已经支持 Apple Silicon

### 2. 更新 gradle.properties
```properties
# 移除或更新这行，使用 ARM64 版本的 JDK
# org.gradle.java.home=/path/to/arm64/jdk
```

### 3. 验证配置
运行以下命令检查 JDK 架构：
```bash
java -version
# 应该看到类似 "aarch64" 或 "arm64" 的输出
```

### 4. 清理并重新构建
```bash
./gradlew clean
./gradlew build
```

### 5. 运行插件
```bash
./gradlew runIde
```

## 性能优化建议

### 1. 使用原生依赖
确保所有依赖都有 ARM64 版本：
- JCEF（IntelliJ 内置版本已支持）
- GraalVM（如果需要，使用 ARM64 版本）

### 2. JVM 参数优化
在 `build.gradle.kts` 中已添加：
```kotlin
if (System.getProperty("os.arch") == "aarch64" || System.getProperty("os.arch") == "arm64") {
    systemProperty("idea.platform.arch", "aarch64")
}
```

### 3. 内存配置
Apple Silicon Mac 通常有统一内存架构，可以适当增加内存分配：
```properties
org.gradle.jvmargs=-Xmx6144m -XX:+UseG1GC
```

## 故障排查

### 检查当前架构
在插件中添加日志：
```kotlin
LOG.info("Running on architecture: ${System.getProperty("os.arch")}")
LOG.info("JVM vendor: ${System.getProperty("java.vendor")}")
LOG.info("JVM version: ${System.getProperty("java.version")}")
```

### 常见问题
1. **仍然显示 x64**：确保使用的是 ARM64 版本的 IntelliJ IDEA
2. **性能问题**：检查 Activity Monitor，确保进程显示为 "Apple" 而不是 "Intel"
3. **依赖冲突**：某些旧依赖可能只有 x64 版本，需要更新或替换

## 参考链接
- [IntelliJ Platform SDK - Apple Silicon Support](https://plugins.jetbrains.com/docs/intellij/ide-development-instance.html#apple-silicon-support)
- [Azul Zulu for Apple Silicon](https://www.azul.com/downloads/?os=macos&architecture=arm-64-bit)
- [JetBrains Runtime Releases](https://github.com/JetBrains/JetBrainsRuntime/releases)