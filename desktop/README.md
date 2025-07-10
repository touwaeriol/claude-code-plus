# Claude Code Plus 桌面应用

独立桌面应用，支持跨平台运行（Windows、macOS、Linux）。

## 启动方式

### 1. 开发模式运行
```bash
# 运行增强版桌面应用（推荐）
./gradlew :desktop:run

# 或者直接使用 gradlew run（项目根目录）
./gradlew :desktop:run
```

### 2. 生产环境运行
```bash
# 构建可分发版本
./gradlew :desktop:createDistributable

# 运行可分发版本
./gradlew :desktop:runDistributable

# 构建并运行 Release 版本
./gradlew :desktop:runRelease
```

### 3. 自定义测试任务
```bash
# 会话管理测试
./gradlew :desktop:runSessionTest

# 完整会话管理测试
./gradlew :desktop:runFullSessionTest

# 会话面板测试
./gradlew :desktop:runSessionPanelTest
```

## 功能特性

### 基础功能
- 与 Claude AI 实时对话
- 流式响应显示
- 多模型支持（Opus、Sonnet）
- 会话历史持久化

### 增强功能
- 多标签管理
- 全局搜索对话历史
- 提示词模板管理
- 对话导出（Markdown、HTML、JSON）
- 上下文管理

### 快捷键
- `Ctrl+T`: 新建标签
- `Ctrl+W`: 关闭当前标签
- `Ctrl+Tab`: 切换标签
- `Ctrl+F`: 全局搜索
- `Ctrl+P`: 提示词模板
- `Ctrl+E`: 导出对话
- `Ctrl+Shift+O`: 对话管理器

## 应用版本

### 基础版本（Main.kt）
简化版本，包含基本的 Claude 对话功能。

### 增强版本（EnhancedMain.kt）
完整版本，包含所有高级功能，如多标签、搜索、导出等。

**当前默认启动：增强版本**

## 构建和分发

### 构建本地可执行文件
```bash
# 构建当前平台的分发包
./gradlew :desktop:createDistributable

# 分发包位置：desktop/build/compose/binaries/main/app/
```

### 构建安装包
```bash
# 构建安装包（DMG、MSI、DEB）
./gradlew :desktop:packageDistributionForCurrentOS

# 安装包位置：desktop/build/compose/binaries/main/
```

## 技术栈

- **Kotlin**: 2.1.10
- **Compose Desktop**: 1.8.0-alpha04
- **Jewel UI**: 0.28.0
- **Coroutines**: 1.7.3

## 故障排除

### 常见问题

1. **图标资源找不到**
   - 警告信息：`Resource 'expui/general/add.svg' not found`
   - 这是 Jewel UI 的已知问题，不影响功能

2. **应用启动缓慢**
   - 首次启动需要初始化文件索引
   - 后续启动会更快

3. **Claude CLI 连接问题**
   - 确保已安装 Claude CLI
   - 确保 Claude CLI 已登录
   - 检查网络连接

### 日志调试
应用启动时会输出详细的日志信息，可用于问题排查。