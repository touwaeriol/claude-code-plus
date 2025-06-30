# Claude Code Plus 项目文档

idea 平台代码拉取到了本地目录 /Users/erio/codes/idea/intellij-community，你可以阅读相关源代码
插件版本为 25.1.2 ，对应 idea平台版本为 251.26094

使用 jenv 切换 jdk 版本

需求文档 [REQUIREMENTS.md](docs/REQUIREMENTS.md)，我提出新需求时自动更新
进度记录文档 [进度文档.md](docs/进度文档.md) 记录进度，你修改后自动更新

## UI 开发规范

### 🎨 Jewel UI 组件规范
- **[Jewel 组件索引目录](.cursor/rules/jewel-component-index.mdc)** - 🔥 **Cursor AI 始终读取**，43个组件快速查找
- **[Jewel 组件完整使用规范](.cursor/rules/jewel-components.mdc)** - 📖 **详细使用手册**，包含所有组件用法和最佳实践
- **[代码风格和最佳实践](.cursor/rules/code-style.mdc)** - Kotlin 代码风格、状态管理、错误处理规范

### UI 组件迁移
- [Jewel 组件迁移指南](docs/JEWEL_COMPONENT_MIGRATION.md) - 详细的组件迁移指南
  - 原生组件到 Jewel 组件的对照表
  - 完整的迁移示例和代码对比
  - 注意事项和兼容性说明
  - 渐进式迁移策略

## 技术文档索引

详细技术文档请查看：[docs/README.md](docs/README.md)

### 核心设计文档
- [需求文档](docs/REQUIREMENTS.md) - 项目功能需求和 UI 设计规范
- [架构设计](docs/ARCHITECTURE.md) - 系统整体架构设计  
- [功能特性](docs/FEATURES.md) - 项目功能特性列表

### UI 开发文档
- [工具窗口重设计](docs/TOOLWINDOW_REDESIGN.md) - 工具窗口UI重设计方案
- [Jewel 组件迁移指南](docs/JEWEL_COMPONENT_MIGRATION.md) - 详细的组件迁移指南
- [Claude消息类型](docs/CLAUDE_MESSAGE_TYPES.md) - Claude消息类型定义

### 开发进度
- [进度文档](docs/进度文档.md) - 开发进度记录和功能实现追踪

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
1. 查看 [开发规范](.cursor/rules/README.mdc) 了解整体规范要求
2. 参考 [Jewel 组件规范](.cursor/rules/jewel-component-index.mdc) 进行组件选择和使用  
3. 遵循 [代码风格](.cursor/rules/code-style.mdc) 规范
4. 参考 [迁移指南](docs/JEWEL_COMPONENT_MIGRATION.md) 进行组件替换
5. 更新 [进度文档](docs/进度文档.md) 记录开发进度

### 💡 Cursor 配置建议
在 Cursor 设置中添加以下配置以获得最佳体验：
```json
"workbench.editorAssociations": {
  "*.mdc": "default"
}
```