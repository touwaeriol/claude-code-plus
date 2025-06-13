# Claude Code Plus 插件测试指南

## 快速开始

### 1. 运行插件（推荐方式）

在项目根目录执行：
```bash
./gradlew runIde
```

这将启动一个新的 IntelliJ IDEA 实例，其中已安装你的插件。

### 2. 构建插件

```bash
./gradlew buildPlugin
```

构建完成后，插件文件将生成在：
- `build/distributions/claude-code-plus-1.0-SNAPSHOT.zip`

### 3. 手动安装测试

1. 打开 IntelliJ IDEA
2. 进入 `Settings/Preferences` → `Plugins`
3. 点击齿轮图标 ⚙️ → `Install Plugin from Disk...`
4. 选择 `build/distributions/claude-code-plus-1.0-SNAPSHOT.zip`
5. 重启 IDE

## 开发调试

### 使用 IntelliJ IDEA 调试

1. 在 IntelliJ IDEA 中打开项目
2. 点击右上角的运行配置下拉菜单
3. 选择 `Run Plugin` 配置（如果没有会自动创建）
4. 点击调试按钮 🐛

### 查看插件日志

沙盒实例的日志文件位置：
```
build/idea-sandbox/system/log/idea.log
```

### 常用 Gradle 任务

```bash
# 清理构建
./gradlew clean

# 准备沙盒环境
./gradlew prepareSandbox

# 运行 IDE
./gradlew runIde

# 构建插件
./gradlew buildPlugin

# 验证插件兼容性
./gradlew runPluginVerifier

# 运行测试
./gradlew test
```

## 测试要点

### 1. 工具窗口测试

运行插件后：
- 检查右侧是否出现 "Claude Code" 工具窗口
- 点击工具窗口图标是否能正常打开/关闭
- 测试聊天界面是否正常显示

### 2. 功能测试

- **消息发送**：在输入框输入消息，按回车发送
- **快捷键**：测试 `Ctrl+Shift+C` (Windows/Linux) 或 `Cmd+Shift+C` (macOS) 是否能打开窗口
- **主题切换**：切换 IDE 主题，检查插件 UI 是否自适应

### 3. @ 文件引用测试

- 在输入框输入 `@` 字符
- 检查是否触发文件补全提示
- 选择文件后是否正确转换为绝对路径

## 常见问题

### 1. Gradle Wrapper 未找到

如果遇到 `./gradlew: No such file or directory` 错误，需要先生成 Gradle Wrapper：

```bash
gradle wrapper --gradle-version 8.5
```

### 2. JDK 版本问题

确保使用 JDK 17 或更高版本：
```bash
java -version
```

### 3. 构建失败

清理并重新构建：
```bash
./gradlew clean buildPlugin
```

### 4. 插件未显示

- 检查 `plugin.xml` 配置是否正确
- 查看 idea.log 中的错误信息
- 确保所有依赖都已正确配置

## 开发技巧

### 1. 热重载

在 2020.2+ 版本的 IDE 中，修改代码后：
1. 运行 `./gradlew buildPlugin`
2. 切换到沙盒 IDE 实例
3. 插件会自动重新加载（无需重启）

### 2. 远程调试

如需远程调试，可以在 `build.gradle.kts` 中配置：

```kotlin
tasks {
    runIde {
        jvmArgs = listOf(
            "-Xdebug",
            "-Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"
        )
    }
}
```

### 3. 自定义沙盒目录

在 `build.gradle.kts` 中配置：

```kotlin
intellij {
    sandboxDir = file("${project.buildDir}/custom-sandbox")
}
```

## 发布前检查

1. 运行所有测试：`./gradlew test`
2. 检查插件兼容性：`./gradlew runPluginVerifier`
3. 更新版本号和变更日志
4. 构建最终版本：`./gradlew clean buildPlugin`
5. 在不同版本的 IDE 中测试
