# Claude Code Plus 技术文档

本目录包含 Claude Code Plus 项目的技术设计文档和实现指南。

## 文档列表

### 核心设计文档
- [**需求文档**](REQUIREMENTS.md) - 项目功能需求和 UI 设计规范
- [**架构设计**](ARCHITECTURE.md) - 系统整体架构和模块设计
- [**功能特性**](FEATURES.md) - 功能特性列表和实现状态

### UI 开发文档
- [**工具窗口重设计**](TOOLWINDOW_REDESIGN.md) - 工具窗口 UI 重设计方案
- [**Jewel 组件迁移**](JEWEL_COMPONENT_MIGRATION.md) - 组件迁移指南和对照表
- [**Claude 消息类型**](CLAUDE_MESSAGE_TYPES.md) - Claude 消息类型定义

### 开发进度
- [**进度文档**](进度文档.md) - 开发进度记录和功能实现追踪

## 开发规范

开发时请遵循以下规范：
- 查看 [`.cursor/rules/`](../.cursor/rules/) 目录中的开发规范
- 遵循 [UI 组件使用规范](../.cursor/rules/ui-components.mdc)
- 遵循 [代码风格规范](../.cursor/rules/code-style.mdc)

## 快速开始

1. 阅读 [需求文档](REQUIREMENTS.md) 了解项目目标
2. 查看 [架构设计](ARCHITECTURE.md) 了解系统结构
3. 参考 [Jewel 组件迁移](JEWEL_COMPONENT_MIGRATION.md) 进行 UI 开发
4. 更新 [进度文档](进度文档.md) 记录开发进度

## 快速导航

### 开发者指南
如果您是新加入的开发者，建议按以下顺序阅读文档：

1. **了解项目**：[ARCHITECTURE.md](ARCHITECTURE.md) → [FEATURES.md](FEATURES.md)
2. **技术实现**：[TOOLWINDOW_REDESIGN.md](TOOLWINDOW_REDESIGN.md) → [JEWEL_COMPONENT_MIGRATION.md](JEWEL_COMPONENT_MIGRATION.md)
3. **消息格式**：[CLAUDE_MESSAGE_TYPES.md](CLAUDE_MESSAGE_TYPES.md) → [CONTEXT_MESSAGE_FORMAT.md](CONTEXT_MESSAGE_FORMAT.md)
4. **具体需求**：[REQUIREMENTS.md](REQUIREMENTS.md) → [CONTEXT_SELECTION_REQUIREMENTS.md](CONTEXT_SELECTION_REQUIREMENTS.md)

### 功能开发
- **上下文功能**：[CONTEXT_MESSAGE_FORMAT.md](CONTEXT_MESSAGE_FORMAT.md) - 完整的上下文系统设计
- **UI 组件**：[JEWEL_COMPONENT_OPTIMIZATION.md](JEWEL_COMPONENT_OPTIMIZATION.md) - 组件开发指南
- **消息处理**：[CLAUDE_MESSAGE_TYPES.md](CLAUDE_MESSAGE_TYPES.md) - 消息格式规范

### 问题排查
- **架构问题**：参考 [ARCHITECTURE.md](ARCHITECTURE.md)
- **组件问题**：参考 [JEWEL_COMPONENT_MIGRATION.md](JEWEL_COMPONENT_MIGRATION.md)
- **消息格式问题**：参考 [CONTEXT_MESSAGE_FORMAT.md](CONTEXT_MESSAGE_FORMAT.md)

## 文档维护

请在进行重要功能开发时及时更新相关文档，确保文档与代码保持同步。 