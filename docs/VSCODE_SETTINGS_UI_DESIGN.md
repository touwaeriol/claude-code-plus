# VS Code 设置界面 UI 设计原型

## 📋 概述

本文档描述 VS Code 扩展设置界面的 UI 设计原型，目标是使用 **Vue 3 + Element Plus** 1:1 复刻 JetBrains 版本的设计风格和内容。

---

## 🎯 设计目标

1. **内容完全一致** - 所有设置项、分组、描述文字完全对齐 JetBrains 版本
2. **布局完全一致** - 左侧导航 + 右侧内容区的布局结构
3. **交互完全一致** - 相同的控件类型和操作方式
4. 使用 **Vue 3 Composition API** 开发
5. 使用 **Element Plus** 组件库
6. 适配 **VS Code 主题** (亮色/暗色)

---

## 📐 整体布局

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ Settings                                                                     │
│ Claude Code Plus                                                             │
├─────────────────────┬───────────────────────────────────────────────────────┤
│                     │                                                       │
│  • ⚡ Claude Code   │  [页面标题]                                           │
│  • 🤖 Codex         │                                                       │
│  • 📝 Git Generate  │  ┌─ 分组标题 ──────────────────────────────────────┐ │
│  • 🔌 MCP           │  │  设置项内容...                                   │ │
│                     │  └─────────────────────────────────────────────────┘ │
│                     │                                                       │
└─────────────────────┴───────────────────────────────────────────────────────┘
```

---

## 📄 页面 1: Claude Code Settings

### 概述
Claude Code 设置页包含 **两个标签页**：General 和 Agents

---

### Tab 1: General (通用设置)

#### 分组 1: Default Permissions

| 设置项 | 类型 | 说明 |
|--------|------|------|
| Default bypass permissions | 复选框 | 跳过文件编辑和 bash 命令的确认对话框 |
| | 描述 | "Skip confirmation dialogs for file edits and bash commands." |
| Default auto cleanup contexts | 复选框 | 发送后自动清理已启用的上下文 |
| | 描述 | "Enabled contexts are cleared after send; disabled contexts stay." |
| Permission Mode | 下拉框 | default / acceptEdits / plan / bypassPermissions |
| | 描述 | "default = Ask for each action \| bypassPermissions = Auto-approve all" |
| Include partial messages in stream | 复选框 | 流式传输时包含部分消息（禁用状态） |

#### 分组 2: Runtime Settings

| 设置项 | 类型 | 说明 |
|--------|------|------|
| Node.js path | 路径输入 + Browse | Node.js 可执行文件路径 |
| | placeholder | 自动检测显示版本信息 |
| | 描述 | "Path to Node.js executable. Leave empty to auto-detect from system PATH." |
| Default model | 下拉框 | Claude Opus 4.5 / Sonnet 4.5 / Sonnet 4 / Haiku 4.5 |
| | 描述 | "Opus 4.5 = Most capable \| Sonnet 4.5 = Balanced \| Haiku 4.5 = Fastest" |

#### 分组 3: Custom Models (可折叠)

| 设置项 | 类型 | 说明 |
|--------|------|------|
| 模型表格 | 表格 | 列: Display Name, Model ID |
| | 按钮 | Add / Edit / Remove |

#### 分组 4: Thinking Configuration

| 设置项 | 类型 | 说明 |
|--------|------|------|
| Default thinking | 下拉框 | Off / Low / Medium / High / Very High / Ultra |
| Think tokens | 数字输入 | 范围 1-128000, 步进 256, 默认 2048 |
| Ultra tokens | 数字输入 | 范围 1-128000, 步进 256, 默认 8096 |

---

### Tab 2: Agents (代理设置)

顶部说明文字:
- "Configure custom agents that extend Claude's capabilities."
- "Requires JetBrains MCP to be enabled" (灰色提示)

#### 分组: ExploreWithJetbrains (可折叠)

| 设置项 | 类型 | 说明 |
|--------|------|------|
| Enable | 复选框 | 启用此代理 |
| Model | 下拉框 | (inherit) / opus / sonnet / haiku |
| | 分隔线 | |
| Description | 多行文本 | 2行高度，等宽字体 |
| System Prompt | 多行文本 | 6行高度，等宽字体 |
| Appended System Prompt | 多行文本 | 3行高度 |
| | 描述 | "Appended to CLI's system prompt. Tells AI when/how to use this agent." |
| | 分隔线 | |
| Allowed Tools | 下拉框 + 按钮 | 可编辑下拉 + "+" 按钮添加 |
| | 标签面板 | 显示已添加的工具标签（可删除） |

#### 分组: CodeWithJetbrains (可折叠)

结构与 ExploreWithJetbrains 完全相同

---

## 📄 页面 2: Codex Settings

#### 分组 1: Default Permissions

| 设置项 | 类型 | 说明 |
|--------|------|------|
| Default bypass permissions | 复选框 | 跳过确认对话框 |
| | 描述 | "Skip confirmation dialogs for file edits and bash commands. Use with caution." |
| Default auto cleanup contexts | 复选框 | 发送后自动清理上下文 |
| | 描述 | "Enabled contexts are cleared after send; disabled contexts stay." |

#### 分组 2: Runtime Settings

| 设置项 | 类型 | 说明 |
|--------|------|------|
| Codex path | 路径输入 + Browse | Codex 可执行文件路径 |
| | placeholder | 自动检测显示 "Detecting Codex..." |
| | 描述 | "Path to Codex executable. Leave empty to auto-detect from system PATH." |
| Web search | 复选框 | 允许 Codex 请求网络搜索 |
| | 描述 | "Allow Codex to request web searches (features.web_search_request)." |

#### 分组 3: Model Settings

| 设置项 | 类型 | 说明 |
|--------|------|------|
| Default model | 下拉框 | 模型列表 |
| | 描述 | "gpt-5.2-codex = Codex optimized \| gpt-5.2 = Base model" |

#### 分组 4: Custom Models (可折叠)

| 设置项 | 类型 | 说明 |
|--------|------|------|
| 模型表格 | 表格 | 列: Display Name, Model ID |
| | 按钮 | Add / Edit / Remove |

#### 分组 5: Session Defaults

| 设置项 | 类型 | 说明 |
|--------|------|------|
| Reasoning effort | 下拉框 | 推理深度选项 |
| | 描述 | "Controls reasoning depth for Codex responses." |
| Reasoning summary | 下拉框 | 推理摘要样式 |
| | 描述 | "Summary style for reasoning output when supported." |
| Sandbox mode | 下拉框 | 沙箱模式选项 |
| | 描述 | "Controls file system and network access permissions." |

---

## 📄 页面 3: Git Generate Settings

顶部说明文字:
- "Configure AI-powered Git commit message generation."
- "Git Generate uses built-in Git MCP and default permissions automatically."

| 设置项 | 类型 | 说明 |
|--------|------|------|
| Enable Git Generate | 复选框 | 在提交消息工具栏中显示 Git Generate |

分隔线

| 设置项 | 类型 | 说明 |
|--------|------|------|
| Backend | 下拉框 | Claude / Codex |
| Model | 下拉框 | 根据后端动态切换模型列表 |

#### 分组: Thinking

| 设置项 | 类型 | 说明 |
|--------|------|------|
| Claude Thinking | 下拉框 | 当 Backend=Claude 时显示 |
| Codex Reasoning Effort | 下拉框 | 当 Backend=Codex 时显示 |

| 设置项 | 类型 | 说明 |
|--------|------|------|
| Save session | 复选框 | 保存会话 |

分隔线

#### 分组: System Prompt

| 设置项 | 类型 | 说明 |
|--------|------|------|
| | 描述 | "Instructions for the AI on how to generate commit messages." |
| | 多行文本 | 系统提示词编辑区 |

#### 分组: User Prompt

| 设置项 | 类型 | 说明 |
|--------|------|------|
| | 描述 | "Runtime prompt sent with the code changes. Customize analysis focus here." |
| | 多行文本 | 用户提示词编辑区 |

| 设置项 | 类型 | 说明 |
|--------|------|------|
| Reset to Default | 按钮 | 重置为默认值 |

---

## 📄 页面 4: MCP Settings

顶部说明文字:
- "Configure MCP (Model Context Protocol) servers. [Learn more](https://modelcontextprotocol.io)"
- 注意事项提示文字

#### MCP 服务器表格

| 列名 | 说明 |
|------|------|
| Status | 启用状态指示器（圆点） |
| Name | 服务器名称 |
| Configuration | 配置类型显示 |
| Backends | Claude / Codex / All（可点击编辑） |
| Level | Global / Project |

表格工具栏:
- **Add** 按钮 - 添加自定义服务器
- **Edit** 按钮 - 编辑选中服务器（仅自定义服务器可用）
- **Remove** 按钮 - 删除选中服务器（仅自定义服务器可用）

底部警告:
- "⚠ Proceed with caution and only connect to trusted servers."

#### 内置 MCP 服务器列表

| 服务器名称 | 说明 |
|------------|------|
| JetBrains LSP | IDE 代码搜索、文件索引 |
| JetBrains File | 文件读写编辑 |
| Terminal | 终端执行 |
| Git | Git 操作 |
| User Interaction | 用户交互 |

#### MCP 服务器编辑对话框

内置服务器编辑:
- 启用/禁用开关
- Backends 选择 (Claude / Codex / All)
- Instructions 多行文本 (通用提示词)
- Instructions for Claude 多行文本
- Instructions for Codex 多行文本
- Timeout 数字输入
- Level 选择 (Global / Project)

自定义服务器编辑:
- Name 输入
- JSON Configuration 多行文本
- Level 选择
- 所有内置服务器的配置选项

---

## 🎨 Vue 组件设计

### 项目结构

```
vscode-extension/
├── webview-ui/                       # 新的 Vue 项目
│   ├── package.json
│   ├── vite.config.ts
│   ├── src/
│   │   ├── main.ts
│   │   ├── App.vue
│   │   ├── styles/
│   │   │   ├── variables.scss        # VS Code 主题变量
│   │   │   └── global.scss
│   │   ├── components/
│   │   │   ├── Sidebar.vue           # 左侧导航
│   │   │   ├── SettingsGroup.vue     # 设置分组容器
│   │   │   ├── CollapsibleGroup.vue  # 可折叠分组
│   │   │   ├── SettingItem.vue       # 单个设置项
│   │   │   ├── PathInput.vue         # 文件路径输入
│   │   │   ├── ModelTable.vue        # 模型表格组件
│   │   │   ├── ToolTagsPanel.vue     # 工具标签面板
│   │   │   └── McpServerTable.vue    # MCP 服务器表格
│   │   ├── pages/
│   │   │   ├── ClaudeCodePage.vue    # Claude Code 设置 (含 tabs)
│   │   │   ├── CodexPage.vue         # Codex 设置
│   │   │   ├── GitGeneratePage.vue   # Git Generate 设置
│   │   │   └── McpPage.vue           # MCP 设置
│   │   ├── dialogs/
│   │   │   ├── CustomModelDialog.vue # 自定义模型对话框
│   │   │   ├── McpServerDialog.vue   # MCP 服务器编辑对话框
│   │   │   └── BuiltInMcpDialog.vue  # 内置 MCP 编辑对话框
│   │   ├── stores/
│   │   │   └── settingsStore.ts      # Pinia 状态管理
│   │   └── utils/
│   │       └── vscodeApi.ts          # VS Code API 封装
│   └── index.html
```

### 核心组件

#### 1. SettingsGroup.vue - 设置分组

```vue
<template>
  <el-card class="settings-group" shadow="never">
    <template #header>
      <span class="group-title">{{ title }}</span>
    </template>
    <slot />
  </el-card>
</template>
```

#### 2. CollapsibleGroup.vue - 可折叠分组

```vue
<template>
  <el-collapse v-model="activeNames">
    <el-collapse-item :title="title" :name="name">
      <slot />
    </el-collapse-item>
  </el-collapse>
</template>
```

#### 3. SettingItem.vue - 设置项

支持类型:
- `text` - 文本输入
- `number` - 数字输入
- `select` - 下拉选择
- `checkbox` - 复选框
- `path` - 路径选择
- `textarea` - 多行文本

#### 4. ModelTable.vue - 模型表格

```vue
<template>
  <div class="model-table">
    <el-table :data="models" border>
      <el-table-column prop="displayName" label="Display Name" />
      <el-table-column prop="modelId" label="Model ID" />
    </el-table>
    <div class="toolbar">
      <el-button @click="add">Add</el-button>
      <el-button @click="edit" :disabled="!selected">Edit</el-button>
      <el-button @click="remove" :disabled="!selected">Remove</el-button>
    </div>
  </div>
</template>
```

#### 5. McpServerTable.vue - MCP 服务器表格

```vue
<template>
  <div class="mcp-table">
    <el-table :data="servers" @row-dblclick="editServer">
      <el-table-column label="Status" width="60">
        <template #default="{ row }">
          <span :class="['status-dot', row.enabled ? 'enabled' : 'disabled']" />
        </template>
      </el-table-column>
      <el-table-column prop="name" label="Name" />
      <el-table-column prop="configuration" label="Configuration" />
      <el-table-column label="Backends">
        <template #default="{ row }">
          <el-tag @click="editBackends(row)">{{ row.backends }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="level" label="Level" />
    </el-table>
    <div class="toolbar">
      <el-button @click="add">Add</el-button>
      <el-button @click="edit" :disabled="isBuiltIn">Edit</el-button>
      <el-button @click="remove" :disabled="isBuiltIn">Remove</el-button>
    </div>
  </div>
</template>
```

---

## 🎨 主题适配

### VS Code 主题变量映射

```scss
:root {
  // Element Plus 变量覆盖
  --el-color-primary: var(--vscode-focusBorder);
  --el-bg-color: var(--vscode-editor-background);
  --el-bg-color-overlay: var(--vscode-editorWidget-background);
  --el-text-color-primary: var(--vscode-foreground);
  --el-text-color-regular: var(--vscode-foreground);
  --el-text-color-secondary: var(--vscode-descriptionForeground);
  --el-border-color: var(--vscode-panel-border);
  
  // 输入框
  --el-input-bg-color: var(--vscode-input-background);
  --el-input-text-color: var(--vscode-input-foreground);
  --el-input-border-color: var(--vscode-input-border);
  
  // 按钮
  --el-button-bg-color: var(--vscode-button-background);
  --el-button-text-color: var(--vscode-button-foreground);
  
  // 菜单
  --el-menu-bg-color: var(--vscode-sideBar-background);
  --el-menu-text-color: var(--vscode-foreground);
  --el-menu-hover-bg-color: var(--vscode-list-hoverBackground);
  --el-menu-active-color: var(--vscode-list-activeSelectionForeground);
}
```

---

## ✅ 完整复刻对照表

### Claude Code 页面

| JetBrains 内容 | VS Code 设计 | 状态 |
|---------------|--------------|------|
| General / Agents 标签页 | `<el-tabs>` | ✅ |
| Default Permissions 分组 | `<SettingsGroup>` | ✅ |
| - Default bypass permissions | `<el-checkbox>` | ✅ |
| - Default auto cleanup contexts | `<el-checkbox>` | ✅ |
| - Permission Mode 下拉 | `<el-select>` | ✅ |
| - Include partial messages | `<el-checkbox>` disabled | ✅ |
| Runtime Settings 分组 | `<SettingsGroup>` | ✅ |
| - Node.js path + Browse | `<PathInput>` | ✅ |
| - Default model 下拉 | `<el-select>` | ✅ |
| Custom Models 可折叠分组 | `<CollapsibleGroup>` | ✅ |
| - 模型表格 | `<ModelTable>` | ✅ |
| - Add/Edit/Remove 按钮 | `<el-button>` | ✅ |
| Thinking Configuration 分组 | `<SettingsGroup>` | ✅ |
| - Default thinking 下拉 | `<el-select>` | ✅ |
| - Think tokens 数字输入 | `<el-input-number>` | ✅ |
| - Ultra tokens 数字输入 | `<el-input-number>` | ✅ |
| Agents 标签页 | 第二个 Tab | ✅ |
| - ExploreWithJetbrains 可折叠 | `<CollapsibleGroup>` | ✅ |
| - CodeWithJetbrains 可折叠 | `<CollapsibleGroup>` | ✅ |

### Codex 页面

| JetBrains 内容 | VS Code 设计 | 状态 |
|---------------|--------------|------|
| Default Permissions 分组 | `<SettingsGroup>` | ✅ |
| Runtime Settings 分组 | `<SettingsGroup>` | ✅ |
| - Codex path + Browse | `<PathInput>` | ✅ |
| - Web search 复选框 | `<el-checkbox>` | ✅ |
| Model Settings 分组 | `<SettingsGroup>` | ✅ |
| Custom Models 可折叠 | `<CollapsibleGroup>` | ✅ |
| Session Defaults 分组 | `<SettingsGroup>` | ✅ |
| - Reasoning effort | `<el-select>` | ✅ |
| - Reasoning summary | `<el-select>` | ✅ |
| - Sandbox mode | `<el-select>` | ✅ |

### Git Generate 页面

| JetBrains 内容 | VS Code 设计 | 状态 |
|---------------|--------------|------|
| Enable Git Generate 复选框 | `<el-checkbox>` | ✅ |
| Backend 下拉 | `<el-select>` | ✅ |
| Model 下拉 | `<el-select>` | ✅ |
| Thinking 分组 | `<SettingsGroup>` | ✅ |
| - 动态切换 Claude/Codex | 条件渲染 | ✅ |
| Save session 复选框 | `<el-checkbox>` | ✅ |
| System Prompt 分组 | `<SettingsGroup>` | ✅ |
| - 多行文本编辑 | `<el-input type="textarea">` | ✅ |
| User Prompt 分组 | `<SettingsGroup>` | ✅ |
| Reset to Default 按钮 | `<el-button>` | ✅ |

### MCP 页面

| JetBrains 内容 | VS Code 设计 | 状态 |
|---------------|--------------|------|
| 说明文字 + Learn more 链接 | HTML + `<a>` | ✅ |
| MCP 服务器表格 | `<McpServerTable>` | ✅ |
| - Status 列 (圆点) | 自定义渲染器 | ✅ |
| - Name 列 | 默认列 | ✅ |
| - Configuration 列 | 自定义渲染器 | ✅ |
| - Backends 列 (可点击) | `<el-tag>` + 点击事件 | ✅ |
| - Level 列 | 默认列 | ✅ |
| 工具栏 Add/Edit/Remove | `<el-button>` | ✅ |
| 底部警告文字 | 黄色警告样式 | ✅ |
| 内置 MCP 编辑对话框 | `<BuiltInMcpDialog>` | ✅ |
| 自定义 MCP 编辑对话框 | `<McpServerDialog>` | ✅ |

---

## 🚀 实现步骤

### Phase 1: 基础框架 (2天)
1. 创建 `webview-ui` Vue 项目
2. 配置 Vite + Element Plus + Pinia
3. 实现主题变量适配
4. 实现 VS Code API 通信层

### Phase 2: 基础组件 (2天)
1. Sidebar.vue - 左侧导航
2. SettingsGroup.vue - 设置分组
3. CollapsibleGroup.vue - 可折叠分组
4. SettingItem.vue - 设置项（多类型）
5. PathInput.vue - 路径输入

### Phase 3: 高级组件 (2天)
1. ModelTable.vue - 模型表格
2. ToolTagsPanel.vue - 工具标签
3. McpServerTable.vue - MCP 表格
4. 对话框组件

### Phase 4: 页面实现 (3天)
1. ClaudeCodePage.vue (含 General + Agents 标签)
2. CodexPage.vue
3. GitGeneratePage.vue
4. McpPage.vue

### Phase 5: 集成测试 (1天)
1. 集成到 VS Code 扩展
2. 测试主题切换
3. 测试设置持久化

**总计: 约 10 天**

---

## 📋 与 JetBrains 版本一致性确认

| 方面 | 一致性 |
|------|--------|
| **导航结构** | ✅ 4个页面完全一致 |
| **分组结构** | ✅ 所有分组完全一致 |
| **设置项** | ✅ 所有设置项完全一致 |
| **控件类型** | ✅ 下拉/输入/复选框/表格等完全对应 |
| **描述文字** | ✅ 所有描述文字完全一致 |
| **交互方式** | ✅ 点击/双击/折叠等完全一致 |
| **对话框** | ✅ 编辑对话框功能完全一致 |
