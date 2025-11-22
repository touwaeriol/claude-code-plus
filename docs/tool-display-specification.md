# 工具展示规范

本文档定义了所有 Claude Code 工具在前端 UI 中的展示规范。

## 设计原则

### 1. 两种展示模式

- **未展开模式（Collapsed）**：显示关键参数摘要，便于快速浏览
- **展开模式（Expanded）**：显示完整参数和详细内容，便于深入查看

### 2. 展示内容原则

- **未展开**：只显示最关键的参数（文件名、数量、摘要等）
- **展开**：显示完整参数和详细内容（完整路径、代码、diff、结果等）

### 3. 默认状态

- **建议默认折叠**（`expanded = false`）
- 用户点击后展开查看详情
- 在 IDE 环境中，可以直接点击打开文件而不展开

---

## 工具展示规范

### 1. Read 工具 📖

**数据模型：**
```typescript
interface ReadToolCall {
  toolType: 'Read'
  input: {
    path: string
    file_path?: string
    offset?: number
    limit?: number
    view_range?: [number, number]
  }
  result?: {
    type: 'success' | 'error'
    content?: string
    error?: string
  }
}
```

**未展开模式：**
- 🔧 图标 + 名称："Read"
- 📄 文件名（从路径提取，不显示完整路径）
- 📏 行范围（如果有）：如 "L10-50"

**展开模式：**
- 📂 完整文件路径（可点击打开文件）
- 📏 行范围详情
- 📄 读取结果（代码高亮显示）
- 📋 复制按钮

---

### 2. Write 工具 ✍️

**数据模型：**
```typescript
interface WriteToolCall {
  toolType: 'Write'
  input: {
    path: string
    file_path?: string
    content: string
  }
  result?: {
    type: 'success' | 'error'
    output?: string
    error?: string
  }
}
```

**未展开模式：**
- 🔧 图标 + 名称："Write"
- 📄 文件名（从路径提取）
- 📦 文件大小（如 "532 B"）

**展开模式：**
- 📂 完整文件路径（可点击打开文件）
- 📦 文件大小
- 📄 **内容预览**（显示 `input.content`，最多 500 字符）
- 📋 复制按钮
- ✅ 执行结果状态

**⚠️ 注意：** 展开后应该显示文件内容预览，而不是只显示路径！

---

### 3. Edit 工具 ✏️

**数据模型：**
```typescript
interface EditToolCall {
  toolType: 'Edit'
  input: {
    file_path: string
    old_string: string
    new_string: string
    replace_all?: boolean
  }
  result?: {
    type: 'success' | 'error'
    output?: string
    error?: string
  }
}
```

**未展开模式：**
- 🔧 图标 + 名称："Edit"
- 📄 文件名
- 🔄 替换类型："单次替换" 或 "全部替换"

**展开模式：**
- 📂 完整文件路径（可点击）
- 🔄 Diff 对比视图（`old_string` vs `new_string`）
- 🏷️ 替换模式标签
- ✅ 执行结果状态

---

### 4. MultiEdit 工具 📝

**数据模型：**
```typescript
interface MultiEditToolCall {
  toolType: 'MultiEdit'
  input: {
    file_path: string
    edits: Array<{
      old_string: string
      new_string: string
    }>
  }
  result?: {
    type: 'success' | 'error'
    output?: string
    error?: string
  }
}
```

**未展开模式：**
- 🔧 图标 + 名称："MultiEdit"
- 📄 文件名
- 🔢 修改数量："3 处修改"

**展开模式：**
- 📂 完整文件路径（可点击）
- 🔢 修改数量
- 📋 修改列表（每个修改可单独展开）
  - 每个修改显示 Diff 对比
- ✅ 执行结果状态

---

### 5. TodoWrite 工具 ✅

**数据模型：**
```typescript
interface TodoWriteToolCall {
  toolType: 'TodoWrite'
  input: {
    todos: Array<{
      content: string
      status: 'pending' | 'in_progress' | 'completed'
      activeForm: string
    }>
  }
}
```

**未展开模式：**
- 🔧 图标 + 名称："TodoWrite"
- 🔢 任务数量："5 个任务"

**展开模式：**
- 📋 完整任务列表
  - 每个任务显示：状态图标 + 内容 + 活动形式
  - 状态图标：
    - ⏳ 待处理（pending）
    - ▶️ 进行中（in_progress）
    - ✅ 已完成（completed）

---

## 实现建议

### 1. 默认状态

建议所有工具默认为**折叠状态**（`expanded = false`），原因：
- 减少初始渲染内容，提升性能
- 便于快速浏览多个工具调用
- 用户可以按需展开查看详情

### 2. IDE 集成优化

在 IDE 环境中：
- 点击工具卡片时，优先执行 IDE 操作（如打开文件）
- 不自动展开详情
- 提供更好的文件导航体验

### 3. 响应式设计

- 未展开模式：单行显示，紧凑布局
- 展开模式：多行显示，充分展示内容
- 支持平滑的展开/折叠动画

---

## 当前问题

### Write 工具展示问题

**问题描述：**
用户反馈 Write 工具展开后显示了文件路径，但应该显示编辑内容。

**当前实现：**
- 未展开：显示文件名 + 大小 ✅
- 展开：显示路径 + 大小 + 内容预览 ✅

**实际问题：**
从截图看，展开后显示的是"路径: C:\Users\...\demo-example.md"，这是正确的。
但用户期望看到的是文件内容预览更加突出。

**改进建议：**
- 保持当前实现（已经有内容预览）
- 优化视觉层次，让内容预览更突出
- 考虑将路径信息缩小或移到次要位置

