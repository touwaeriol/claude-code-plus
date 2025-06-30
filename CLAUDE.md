# Claude Code Plus 项目文档

idea 平台代码拉取到了本地目录 /Users/erio/codes/idea/intellij-community，你可以阅读相关源代码
插件版本为 25.1.2 ，对应 idea平台版本为 251.26094

使用 jenv 切换 jdk 版本

需求文档 [REQUIREMENTS.md](docs/REQUIREMENTS.md)，我提出新需求时自动更新
进度记录文档 [进度文档.md](docs/进度文档.md) 记录进度，你修改后自动更新

## UI 开发规范

### Jewel 组件使用规范
- [Jewel 组件使用规范](.cursor/rules) - **重要：开发时必须遵循的 UI 组件使用规范**
  - 优先使用 Jewel 组件替代原生 Compose 组件
  - 主题系统使用规范和最佳实践
  - 组件导入和样式规范
  - 性能优化和测试规范

### UI 组件迁移
- [Jewel 组件迁移指南](docs/JEWEL_COMPONENT_MIGRATION.md) - 详细的组件迁移指南
  - 原生组件到 Jewel 组件的对照表
  - 完整的迁移示例和代码对比
  - 注意事项和兼容性说明
  - 渐进式迁移策略

## 技术文档索引

### 核心功能
- [Claude CLI JSON解析问题解决方案](docs/Claude-CLI-JSON解析问题解决方案.md) - JSON解析异常的排查和修复方案
- [架构设计](docs/ARCHITECTURE.md) - 系统整体架构设计
- [功能特性](docs/FEATURES.md) - 项目功能特性列表
- [会话管理](docs/SESSION_MANAGEMENT.md) - 聊天会话管理机制

### UI 设计
- [工具窗口重设计](docs/TOOLWINDOW_REDESIGN.md) - 工具窗口UI重设计方案
- [Claude消息类型](docs/CLAUDE_MESSAGE_TYPES.md) - Claude消息类型定义

### 开发指南
- [CLI包装器使用指南](docs/CLI_WRAPPER_USAGE.md) - CLI包装器的使用方法

## 开发规范总结

### 🎯 UI 组件使用原则
1. **优先级**：Jewel 组件 > 原生 Compose 组件 > 自定义组件
2. **主题一致性**：使用 `JewelTheme.globalColors.*` 而不是硬编码颜色
3. **导入规范**：优先导入 `org.jetbrains.jewel.ui.component.*`

### 📝 核心组件替换
```kotlin
// 文本：Text -> org.jetbrains.jewel.ui.component.Text
// 滚动：LazyColumn -> VerticallyScrollableContainer  
// 按钮：Button -> DefaultButton
// 输入：BasicTextField -> TextField
// 分隔：Divider -> org.jetbrains.jewel.ui.component.Divider
```

### 🛠 开发工作流
1. 查看 [`.cursor/rules`](.cursor/rules) 了解组件使用规范
2. 参考 [迁移指南](docs/JEWEL_COMPONENT_MIGRATION.md) 进行组件替换
3. 更新 [进度文档](docs/进度文档.md) 记录开发进度
4. 遵循文档中的最佳实践和性能优化建议