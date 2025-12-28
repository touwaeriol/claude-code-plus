# Codex Settings Components

这个目录包含 Codex 后端的 IDEA 设置界面组件。

## 文件说明

### `CodexConfigurable.kt`

IDEA 设置界面的 UI 组件，实现了 `Configurable` 接口。

**功能特性**:

1. **Codex 二进制路径配置**
   - 文本输入框 + 文件浏览按钮
   - 支持手动输入或通过文件选择器选择

2. **自动检测功能**
   - 自动检测按钮
   - 在常见位置搜索 Codex 可执行文件
   - 支持 Windows、macOS、Linux

3. **模型提供者选择**
   - 下拉框选择：OpenAI、Ollama、Anthropic、Custom
   - 用于配置 Codex 使用的 LLM 提供者

4. **默认沙箱模式**
   - ReadOnly: 只读模式，仅允许读取文件
   - WorkspaceWrite: 工作区写入，允许在工作区内修改文件
   - FullAccess: 完全访问，允许所有文件操作
   - 下拉框选择，带有说明文本

5. **测试连接功能**
   - 测试连接按钮
   - 验证二进制文件路径是否有效
   - 执行 `--version` 命令测试 Codex 可用性
   - 显示测试结果状态

**UI 布局**:
```
┌─────────────────────────────────────────────┐
│ Codex 二进制路径: [________________] [浏览]  │
│                   [自动检测]                │
│                                             │
│ 模型提供者:       [OpenAI           ▼]     │
│                                             │
│ 默认沙箱模式:     [WorkspaceWrite   ▼]     │
│                   工作区写入 - 允许在...     │
│                                             │
│ [测试连接]                                  │
│ ✓ 连接成功: Codex v1.0.0                   │
└─────────────────────────────────────────────┘
```

### `CodexSettings.kt`

设置持久化服务，使用 IntelliJ Platform 的 `PersistentStateComponent`。

**存储机制**:
- 使用 XML 序列化
- 存储在项目级别配置文件：`.idea/codex-settings.xml`
- 自动保存和加载

**属性**:
- `binaryPath: String` - Codex 二进制文件路径
- `modelProvider: String` - 模型提供者名称
- `sandboxMode: String` - 默认沙箱模式
- `enabled: Boolean` - Codex 是否启用
- `lastTestResult: String` - 最后一次连接测试结果

**工具方法**:
- `isValid(): Boolean` - 检查配置是否有效
- `getSummary(): String` - 获取配置摘要信息
- `getModelProviderEnum()` - 获取枚举类型的模型提供者
- `getSandboxModeEnum()` - 获取枚举类型的沙箱模式

## 使用方式

### 在插件中注册 Configurable

在 `plugin.xml` 中注册：

```xml
<extensions defaultExtensionNs="com.intellij">
    <projectConfigurable
        parentId="tools"
        instance="com.asakii.plugin.settings.CodexConfigurable"
        id="com.asakii.plugin.settings.CodexConfigurable"
        displayName="Codex Backend"/>
</extensions>
```

### 访问设置

在代码中访问设置：

```kotlin
// 获取设置实例
val settings = CodexSettings.getInstance(project)

// 读取配置
val binaryPath = settings.binaryPath
val modelProvider = settings.getModelProviderEnum()
val sandboxMode = settings.getSandboxModeEnum()

// 检查配置是否有效
if (settings.isValid()) {
    // 启动 Codex 进程
    val process = ProcessBuilder(binaryPath, "--help").start()
}

// 修改配置
settings.binaryPath = "/path/to/codex"
settings.setModelProvider(CodexConfigurable.ModelProvider.OPENAI)
settings.setSandboxMode(CodexConfigurable.SandboxMode.WORKSPACE_WRITE)
```

### 自动检测逻辑

自动检测会在以下位置搜索 Codex 二进制文件：

**Windows**:
- `C:\Program Files\Codex\codex.exe`
- `C:\Program Files (x86)\Codex\codex.exe`
- `%LOCALAPPDATA%\Codex\codex.exe`
- `%USERPROFILE%\.codex\codex.exe`

**macOS**:
- `/usr/local/bin/codex`
- `/opt/homebrew/bin/codex`
- `~/.codex/codex`
- `/Applications/Codex.app/Contents/MacOS/codex`

**Linux**:
- `/usr/local/bin/codex`
- `/usr/bin/codex`
- `~/.local/bin/codex`
- `~/.codex/codex`

## 测试连接流程

1. 用户点击"测试连接"按钮
2. 验证二进制文件路径是否为空
3. 验证文件是否存在
4. 验证文件是否可执行
5. 执行 `codex --version` 命令
6. 根据退出码和输出显示结果：
   - ✓ 成功: 绿色，显示版本信息
   - ✗ 失败: 红色，显示错误信息

## 集成到主项目

要将这些设置集成到主项目，需要：

1. **复制文件**:
   - 将 `CodexConfigurable.kt` 复制到主项目的 `jetbrains-plugin/src/main/kotlin/com/asakii/plugin/settings/`
   - 将 `CodexSettings.kt` 复制到同一目录

2. **注册服务**:
   - 在 `plugin.xml` 中添加 `projectConfigurable` 扩展点

3. **更新 PluginConfig.kt**:
   - 添加 Codex 相关的配置检查
   - 在启动时读取 CodexSettings

4. **在前端同步设置**:
   - 在 HTTP API Server 中添加端点：`/api/settings/codex`
   - 将 CodexSettings 推送到前端

## 注意事项

1. **线程安全**: UI 操作都在 EDT 线程中执行（通过 `SwingUtilities.invokeLater`）
2. **错误处理**: 自动检测和测试连接都有完善的错误处理
3. **用户体验**: 状态标签实时更新，颜色编码（蓝色=进行中，绿色=成功，红色=失败）
4. **持久化**: 设置自动保存到项目配置文件中

## 未来改进

- [ ] 添加"下载 Codex"按钮（如果未安装）
- [ ] 支持配置 Codex 环境变量
- [ ] 支持配置 MCP 服务器列表
- [ ] 添加"重置为默认值"按钮
- [ ] 添加配置导入/导出功能
