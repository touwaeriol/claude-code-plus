# Claude Code Plus 文档中心

本目录包含 Claude Code Plus 项目的所有技术文档和设计方案。

## 📚 文档索引

### 架构与设计

- **[VUE_MIGRATION_PLAN.md](VUE_MIGRATION_PLAN.md)** - Vue 前端架构迁移方案
  - 完整的迁移计划和时间表
  - 新架构设计和通信协议
  - 详细的实施步骤 (6周计划)
  - 风险评估和应对措施

### 项目概览

- **[../CLAUDE.md](../CLAUDE.md)** - 项目主文档索引
  - Claude Code Plus 整体架构
  - 各模块功能说明
  - 开发规范和最佳实践

## 🚀 快速导航

### 正在进行的工作

**分支**: `feat/vue-frontend-migration`
**目标**: 将 UI 层从 Compose Desktop 迁移到 Vue 3 + JCEF
**状态**: 📝 规划阶段
**文档**: [VUE_MIGRATION_PLAN.md](VUE_MIGRATION_PLAN.md)

### 核心架构变更

```
当前架构: Kotlin Plugin + Compose Desktop UI
         ↓
目标架构: Kotlin Backend + Vue 3 Frontend (JCEF)
```

**关键优势**:
- ✅ 成熟的前端生态 (Vue 3 + npm)
- ✅ 更好的开发体验 (热重载 + DevTools)
- ✅ 更丰富的 UI 组件库
- ✅ 已验证的方案 (GitHub Copilot Chat)

## 📖 如何使用本文档

1. **新开发者**: 先阅读 [../CLAUDE.md](../CLAUDE.md) 了解项目整体结构
2. **参与迁移**: 阅读 [VUE_MIGRATION_PLAN.md](VUE_MIGRATION_PLAN.md) 了解迁移计划
3. **日常开发**: 参考 CLAUDE.md 中的模块文档和开发规范

## 🔄 文档更新

所有文档遵循"代码即文档"原则:
- 架构变更时必须更新相关文档
- 新功能开发前先更新设计文档
- 文档版本与代码分支保持同步

---

**维护者**: Claude Code Plus Team
**最后更新**: 2025-01-03
