### Git 提交规范

**重要**: 禁止使用终端命令（git commit, git add, git push 等）进行版本控制操作。
必须使用 jetbrains_git MCP 工具。

### 提交工作流

1. `GetVcsChanges()` → 获取变更列表
2. 分析变更，使用 `SelectFiles` / `DeselectFiles` 调整文件选择
3. `SetCommitMessage()` → 生成并填入提交消息
4. **必须**使用 `AskUserQuestion` 询问用户确认
5. 用户确认后，调用 `CommitChanges()` 执行提交

### 使用时机

用于与 IDEA 的 VCS/Git 集成交互：读取变更、设置提交消息、检查状态。

### 文件选择工具

- `SelectFiles(paths, mode)` → 在 Commit 面板选中文件（mode: "replace" 覆盖或 "add" 追加）
- `DeselectFiles(paths)` → 取消选中文件
- `SelectAllFiles()` → 全选所有变更文件
- `DeselectAllFiles()` → 取消全选

### 提交消息规范 (Conventional Commits)

遵循 Conventional Commits 格式：

```
<type>(<scope>): <description>

[可选正文]

[可选脚注]
```

**类型**:
- `feat`: 新功能
- `fix`: 错误修复
- `docs`: 仅文档更改
- `style`: 代码风格更改（格式化、缺少分号等）
- `refactor`: 代码重构，不涉及功能或错误修复
- `perf`: 性能优化
- `test`: 添加或修改测试
- `chore`: 构建过程、辅助工具等更改
- `ci`: CI 配置更改
- `build`: 构建系统或外部依赖更改

**示例**:
- `feat(auth): add OAuth2 login support`
- `fix(api): resolve null pointer exception in user endpoint`
- `docs: update README with installation instructions`
- `refactor(core): simplify data processing logic`

### 注意事项

- 提交前必须等待用户审核确认
- 在 `CommitChanges` 中使用 `push=true` 可一步完成提交并推送
