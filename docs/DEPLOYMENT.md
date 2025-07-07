# Claude Code Plus 部署指南

Claude Code Plus 支持两种部署方式，满足不同使用场景的需求：

## 部署方式

### 1. IntelliJ IDEA 插件（jetbrains-plugin）

适用于 JetBrains IDE 用户，深度集成 IDE 功能。

**特点：**
- 完整的 IDE 集成体验
- 支持项目文件索引和代码导航
- 与 IDE 编辑器无缝协作
- 支持 MCP 扩展配置

**构建命令：**
```bash
./gradlew :jetbrains-plugin:buildPlugin
```

**安装方式：**
- 通过 JetBrains Marketplace 安装（发布后）
- 本地安装：Settings → Plugins → Install Plugin from Disk

### 2. 独立桌面应用（desktop）

适用于不使用 JetBrains IDE 或需要独立运行的用户。

**特点：**
- 无需安装 IntelliJ IDEA
- 轻量级独立应用
- 支持基本的文件浏览和编辑
- 跨平台支持（Windows/macOS/Linux）

**构建命令：**
```bash
# 运行开发版本
./gradlew :desktop:run

# 构建发布包
./gradlew :desktop:packageDistributionForCurrentOS
```

**支持的包格式：**
- macOS: DMG
- Windows: MSI
- Linux: DEB

## 架构设计

### 模块结构

```
claude-code-plus/
├── cli-wrapper/          # Claude CLI 封装（共享）
├── toolwindow/           # UI 组件库（共享）
├── jetbrains-plugin/     # IntelliJ IDEA 插件
└── desktop/              # 独立桌面应用
```

### 代码复用策略

1. **共享组件**（toolwindow 模块）
   - ChatView - 主聊天界面
   - UnifiedInputArea - 统一输入区域
   - SessionListPanel - 会话管理
   - 富文本渲染组件

2. **服务接口抽象**
   - FileIndexService - 文件索引服务
   - ProjectService - 项目服务
   - 不同平台提供各自的实现

3. **CLI 集成**（cli-wrapper 模块）
   - 统一的 Claude CLI 调用接口
   - 流式响应处理
   - 会话管理

## 功能对比

| 功能 | IntelliJ 插件 | 桌面应用 |
|------|--------------|----------|
| Claude AI 对话 | ✅ | ✅ |
| 流式响应 | ✅ | ✅ |
| 会话管理 | ✅ | ✅ |
| @ 上下文引用 | ✅ | ✅ |
| IDE 文件索引 | ✅ | ❌ |
| 代码导航跳转 | ✅ | ❌ |
| PSI 语法分析 | ✅ | ❌ |
| MCP 扩展 | ✅ | ✅ |
| 独立运行 | ❌ | ✅ |

## 开发指南

### 环境要求

- JDK 21+
- Kotlin 2.1.10
- IntelliJ IDEA 2025.1.3+（仅插件开发）

### 调试运行

**插件调试：**
```bash
./gradlew :jetbrains-plugin:runIde
```

**桌面应用调试：**
```bash
./gradlew :desktop:run
```

### 发布流程

1. **版本更新**
   - 更新 `build.gradle.kts` 中的版本号
   - 更新 `plugin.xml` 中的 change notes

2. **构建发布包**
   ```bash
   # 插件
   ./gradlew :jetbrains-plugin:buildPlugin
   
   # 桌面应用
   ./gradlew :desktop:packageDistributionForCurrentOS
   ```

3. **发布渠道**
   - 插件：JetBrains Marketplace
   - 桌面应用：GitHub Releases

## 未来规划

- 支持更多 IDE（VS Code、Eclipse）
- Web 版本支持
- 移动端适配
- 更多 AI 模型集成