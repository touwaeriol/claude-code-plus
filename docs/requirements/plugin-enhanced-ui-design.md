# Claude Code Plus IntelliJ 插件增强 UI 实现方案

## 1. 概述

本文档定义了 IntelliJ IDEA 插件在复用 `toolwindow` 模块核心组件基础上的增强 UI 设计，重点关注插件特有的 IDE 集成功能和快捷操作。

## 2. 架构设计

### 2.1 组件复用策略

```
插件 UI 架构
├── toolwindow 模块（核心组件）
│   ├── ChatViewNew（主聊天视图）
│   ├── UnifiedInputArea（输入区域）
│   ├── AssistantMessageDisplay（消息显示）
│   └── CompactToolCallDisplay（工具调用）
│
└── plugin 模块（增强功能）
    ├── IDE Actions（动作系统）
    ├── Editor Gutter（编辑器增强）
    ├── Floating Toolbar（浮动工具栏）
    ├── Quick Chat Dialog（快速对话框）
    └── Status Bar Widget（状态栏组件）
```

## 3. 插件特有 UI 组件

### 3.1 浮动工具栏（Floating Toolbar）

在编辑器中选中代码时自动出现的浮动工具栏：

```
┌──────────────────────────────────────┐
│ 🤖 Ask Claude  ▼                     │
├──────────────────────────────────────┤
│ • 解释代码                           │
│ • 优化性能                           │
│ • 生成测试                           │
│ • 修复问题                           │
│ • 添加注释                           │
│ ─────────────                        │
│ • 新建会话...                        │
│ • 添加到当前会话                     │
└──────────────────────────────────────┘
```

**实现要点**：
- 选中代码超过 10 个字符时显示
- 鼠标移开 500ms 后自动隐藏
- 支持键盘快捷操作（1-5 数字键）

### 3.2 快速对话框（Quick Chat Dialog）

轻量级对话框，用于快速问答，不切换到主工具窗口：

```
┌─────────────────────────────────────────────────┐
│ 💬 Quick Claude                          [×]   │
├─────────────────────────────────────────────────┤
│ 选中代码: UserService.java:45-67               │
│ ┌─────────────────────────────────────────┐   │
│ │ public User findById(Long id) {          │   │
│ │     return userRepository.find(id);      │   │
│ │ }                                         │   │
│ └─────────────────────────────────────────┘   │
├─────────────────────────────────────────────────┤
│ 🤖 这段代码存在潜在的空指针异常风险...         │
│                                                 │
│ 建议添加空值检查：                              │
│ ```java                                         │
│ public User findById(Long id) {                │
│     User user = userRepository.find(id);       │
│     if (user == null) {                        │
│         throw new UserNotFoundException(id);   │
│     }                                           │
│     return user;                               │
│ }                                               │
│ ```                                             │
├─────────────────────────────────────────────────┤
│ 输入问题... (Shift+Enter 发送)          [应用]  │
└─────────────────────────────────────────────────┘
```

**特性**：
- ESC 关闭，不影响主会话
- 支持直接应用代码建议
- 可转换为完整会话

### 3.3 编辑器 Gutter 集成

在代码行号旁添加 AI 辅助图标：

```java
 42  │ public class UserService {
 43 🤖│     // AI: 考虑使用 @Transactional
 44  │     public User createUser(UserDTO dto) {
 45 ⚠️│         User user = new User();  // AI: 未验证输入
 46  │         user.setName(dto.getName());
 47 💡│         // AI 建议: 添加日志记录
 48  │         return userRepository.save(user);
 49  │     }
 50  │ }
```

**图标类型**：
- 🤖 AI 注释/建议
- ⚠️ AI 发现的问题
- 💡 AI 优化建议
- ✅ AI 已审查

### 3.4 状态栏 Widget

在 IDE 底部状态栏显示 Claude 状态：

```
[Java 17] [UTF-8] [Git: main] | 🤖 Claude: Ready (2.5k/200k) | [Memory: 512M]
```

点击展开菜单：
```
┌─────────────────────────────────┐
│ Claude Code Plus               │
├─────────────────────────────────┤
│ ✅ 已连接                       │
│ 📊 Token: 2,456 / 200,000      │
│ 💬 活动会话: 3                  │
│ ─────────────                  │
│ • 打开主窗口                    │
│ • 新建会话                      │
│ • 查看历史                      │
│ • 设置...                       │
└─────────────────────────────────┘
```

### 3.5 智能提示气泡（Smart Hint Balloon）

在代码编辑时提供实时 AI 提示：

```
┌─────────────────────────────────────────┐
│ 💡 AI 提示                              │
│                                         │
│ 检测到您正在编写数据库查询。            │
│ 建议使用参数化查询防止 SQL 注入：       │
│                                         │
│ [查看示例] [忽略] [不再提示]            │
└─────────────────────────────────────────┘
```

## 4. IDE Actions 系统

### 4.1 Action 注册

```xml
<!-- plugin.xml -->
<actions>
    <!-- 主菜单 -->
    <group id="ClaudeCodePlus.MainMenu" text="Claude" popup="true">
        <add-to-group group-id="MainMenu" anchor="before" relative-to-action="HelpMenu"/>
        <action id="ClaudeCodePlus.NewSession" 
                class="com.claudecodeplus.actions.NewSessionAction"
                text="新建会话" 
                description="开始新的 Claude 会话"
                icon="AllIcons.General.Add">
            <keyboard-shortcut keymap="$default" first-keystroke="ctrl alt N"/>
        </action>
    </group>
    
    <!-- 编辑器右键菜单 -->
    <group id="ClaudeCodePlus.EditorPopup">
        <add-to-group group-id="EditorPopupMenu" anchor="after" 
                      relative-to-action="CutCopyPasteGroup"/>
        <separator/>
        <action id="ClaudeCodePlus.ExplainCode" 
                class="com.claudecodeplus.actions.ExplainCodeAction"
                text="使用 Claude 解释"/>
        <action id="ClaudeCodePlus.OptimizeCode" 
                class="com.claudecodeplus.actions.OptimizeCodeAction"
                text="使用 Claude 优化"/>
    </group>
    
    <!-- 工具栏 -->
    <group id="ClaudeCodePlus.ToolbarActions">
        <add-to-group group-id="MainToolBar" anchor="last"/>
        <action id="ClaudeCodePlus.QuickChat" 
                class="com.claudecodeplus.actions.QuickChatAction"
                text="Quick Claude"
                icon="ClaudeIcon">
            <keyboard-shortcut keymap="$default" first-keystroke="ctrl shift SPACE"/>
        </action>
    </group>
</actions>
```

### 4.2 快捷键映射

| 快捷键 | 功能 | 上下文 |
|--------|------|--------|
| `Ctrl+Alt+N` | 新建 Claude 会话 | 全局 |
| `Ctrl+Shift+Space` | 快速 Claude 对话 | 全局 |
| `Alt+Enter` | 显示 AI 建议（在有 AI 提示时） | 编辑器 |
| `Ctrl+Alt+C` | 发送选中代码到 Claude | 编辑器 |
| `Ctrl+Alt+Shift+C` | 添加选中代码到当前会话 | 编辑器 |
| `Ctrl+Alt+H` | 显示/隐藏 Claude 工具窗口 | 全局 |
| `Ctrl+Alt+/` | AI 代码补全 | 编辑器 |

### 4.3 Intention Actions

```kotlin
class ClaudeIntentionAction : IntentionAction {
    override fun getText() = "使用 Claude 修复此问题"
    override fun getFamilyName() = "Claude Code Plus"
    
    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        // 检测光标位置是否有错误或警告
        val caretModel = editor.caretModel
        val offset = caretModel.offset
        return hasErrorAtOffset(file, offset)
    }
    
    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val problem = getProblemAtCaret(editor, file)
        ClaudeQuickFixDialog(project, problem).show()
    }
}
```

## 5. 项目视图集成

### 5.1 文件树装饰器

```
project/
├── 📁 src/
│   ├── 📁 main/
│   │   ├── 📁 java/
│   │   │   └── 📄 UserService.java 🤖 (AI reviewed)
│   │   └── 📁 resources/
│   │       └── 📄 application.yml ⚠️ (AI: 安全建议)
│   └── 📁 test/
│       └── 📄 UserServiceTest.java 💡 (AI: 可改进)
└── 📄 pom.xml ✅ (AI approved)
```

### 5.2 文件右键菜单增强

```
┌──────────────────────────────────┐
│ New                        >    │
│ Cut                             │
│ Copy                            │
│ ─────────────────────────       │
│ 🤖 Claude 操作            >    │
│   • 分析文件                    │
│   • 生成文档                    │
│   • 代码审查                    │
│   • 查找相似文件                │
│   • 生成单元测试                │
└──────────────────────────────────┘
```

## 6. 工具窗口增强

### 6.1 增强的工具栏

在 toolwindow 基础上添加插件特有功能：

```
┌────────────────────────────────────────────────────────────────┐
│ [➕] [📋] [🗑️] [⚙️] │ [👁️] [📌] [🔄] │ 🔍 搜索... │ [⋮] │
└────────────────────────────────────────────────────────────────┘
```

新增按钮：
- 👁️ 预览模式（不执行工具调用）
- 📌 固定会话（防止自动清理）
- 🔄 同步会话（与其他 IDE 窗口）
- ⋮ 更多选项

### 6.2 会话管理器弹窗

点击 📋 显示增强的会话管理器：

```
┌──────────────────────────────────────────────────────┐
│ 会话管理器                                    [×]   │
├──────────────────────────────────────────────────────┤
│ [全部] [今天] [本周] [搜索: ___________]            │
├──────────────────────────────────────────────────────┤
│ 📌 "重构用户认证" - 10分钟前                        │
│    项目: MyProject | Token: 45.2k | 消息: 23        │
│                                                      │
│ 💬 "修复空指针异常" - 2小时前                       │
│    项目: MyProject | Token: 12.3k | 消息: 8         │
│                                                      │
│ 💬 "数据库优化方案" - 昨天                          │
│    项目: DatabaseApp | Token: 67.8k | 消息: 45      │
├──────────────────────────────────────────────────────┤
│ [导入] [导出] [删除选中] [清理历史]          [打开]  │
└──────────────────────────────────────────────────────┘
```

### 6.3 分屏视图

支持分屏查看多个会话：

```
┌─────────────────────┬─────────────────────┐
│ 会话 1              │ 会话 2              │
│                     │                     │
│ 用户: 如何优化？    │ 用户: 解释这段代码  │
│                     │                     │
│ Claude: ...         │ Claude: ...         │
│                     │                     │
└─────────────────────┴─────────────────────┘
```

## 7. 智能功能集成

### 7.1 代码补全集成

```java
public void processUser(User user) {
    // 输入时触发 AI 建议
    user.set█
    
    ┌──────────────────────────────────┐
    │ 🤖 AI 建议:                      │
    │ • setName(String)                │
    │ • setEmail(String) ⭐            │
    │ • setStatus(UserStatus)          │
    │ • setCreatedAt(LocalDateTime)    │
    └──────────────────────────────────┘
}
```

### 7.2 实时错误检测

```java
public User getUser(Long id) {
    return userRepository.findById(id);
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    ⚠️ Claude: 可能返回 null，建议使用 Optional
    
    [应用建议] [查看详情] [忽略]
}
```

### 7.3 重构建议

```
┌────────────────────────────────────────────┐
│ 🔄 Claude 重构建议                         │
├────────────────────────────────────────────┤
│ 检测到重复代码模式：                       │
│                                            │
│ • UserService.java:45-67                  │
│ • OrderService.java:23-45                 │
│ • ProductService.java:89-111              │
│                                            │
│ 建议提取为通用方法                        │
│                                            │
│ [查看建议] [自动重构] [忽略]               │
└────────────────────────────────────────────┘
```

## 8. 通知系统

### 8.1 任务完成通知

```
┌────────────────────────────────────────────┐
│ ✅ Claude 任务完成                         │
│                                            │
│ 已完成代码优化：                           │
│ • 修改 3 个文件                            │
│ • 添加 15 行代码                           │
│ • 删除 8 行冗余代码                        │
│                                            │
│ [查看更改] [撤销] [关闭]                   │
└────────────────────────────────────────────┘
```

### 8.2 智能提醒

```
┌────────────────────────────────────────────┐
│ 💡 Claude 提醒                             │
│                                            │
│ 您已经 2 小时没有提交代码了                │
│ 当前有 5 个文件被修改                      │
│                                            │
│ 是否需要 AI 帮您生成提交信息？             │
│                                            │
│ [生成提交信息] [稍后提醒] [关闭]           │
└────────────────────────────────────────────┘
```

## 9. 上下文感知功能

### 9.1 智能上下文推荐

根据当前编辑位置自动推荐相关文件：

```
┌────────────────────────────────────────────┐
│ 📎 推荐添加的上下文                        │
├────────────────────────────────────────────┤
│ 基于您正在编辑 UserService.java：          │
│                                            │
│ ☑ UserRepository.java (强相关)            │
│ ☑ User.java (实体类)                      │
│ ☐ UserController.java (可能相关)          │
│ ☐ UserServiceTest.java (测试类)           │
│                                            │
│ [全部添加] [添加选中] [取消]               │
└────────────────────────────────────────────┘
```

### 9.2 会话上下文切换

```
当前项目: MyProject
当前分支: feature/user-auth
当前文件: UserService.java

Claude 会话自动包含:
✅ 项目配置 (pom.xml)
✅ 当前类的接口定义
✅ 相关的测试文件
✅ 最近的 Git 更改
```

## 10. 调试集成

### 10.1 断点处的 AI 分析

```
🔴 Breakpoint at line 45
┌────────────────────────────────────────────┐
│ 🤖 Claude 断点分析                         │
├────────────────────────────────────────────┤
│ 变量状态:                                  │
│ • user = null ⚠️                          │
│ • id = 12345                               │
│ • cache = HashMap(size=3)                  │
│                                            │
│ 可能的问题:                                │
│ user 为 null，下一行会抛出 NPE             │
│                                            │
│ [修复建议] [继续调试] [关闭]               │
└────────────────────────────────────────────┘
```

### 10.2 异常分析

```
┌────────────────────────────────────────────┐
│ 🚨 异常被 Claude 捕获                      │
├────────────────────────────────────────────┤
│ NullPointerException at UserService:45     │
│                                            │
│ Claude 分析:                               │
│ findById 返回了 null，但代码未处理         │
│                                            │
│ 建议修复:                                  │
│ ```java                                    │
│ Optional<User> user = findById(id);       │
│ if (user.isPresent()) { ... }             │
│ ```                                        │
│                                            │
│ [应用修复] [查看堆栈] [忽略]               │
└────────────────────────────────────────────┘
```

## 11. Git 集成增强

### 11.1 提交信息生成

```
┌────────────────────────────────────────────┐
│ Commit Changes                   2 files  │
├────────────────────────────────────────────┤
│ Commit Message:                           │
│ ┌──────────────────────────────────────┐  │
│ │                                      │  │
│ └──────────────────────────────────────┘  │
│                                            │
│ [🤖 AI 生成] [📝 模板]                    │
│                                            │
│ Claude 建议的提交信息:                     │
│ ┌──────────────────────────────────────┐  │
│ │ feat: 添加用户认证功能                │  │
│ │                                      │  │
│ │ - 实现 JWT token 生成和验证           │  │
│ │ - 添加登录/登出接口                   │  │
│ │ - 增加权限检查中间件                  │  │
│ └──────────────────────────────────────┘  │
│                                            │
│ [使用此信息] [重新生成] [取消]             │
└────────────────────────────────────────────┘
```

### 11.2 代码审查助手

```
┌────────────────────────────────────────────┐
│ 🔍 Claude Code Review - PR #123            │
├────────────────────────────────────────────┤
│ 文件更改: 5 | 新增: +120 | 删除: -45      │
├────────────────────────────────────────────┤
│ AI 审查结果:                               │
│                                            │
│ ✅ 代码质量: 良好                          │
│ ⚠️ 潜在问题: 2 个                         │
│ 💡 改进建议: 3 个                          │
│                                            │
│ 详细问题:                                  │
│ 1. UserService.java:45 - 缺少空值检查     │
│ 2. config.yml:12 - 硬编码的密钥            │
│                                            │
│ [查看详情] [添加评论] [批准] [请求更改]    │
└────────────────────────────────────────────┘
```

## 12. 性能监控面板

### 12.1 Claude 使用统计

```
┌────────────────────────────────────────────┐
│ 📊 Claude 使用统计                         │
├────────────────────────────────────────────┤
│ 今日 Token 使用: ████████░░ 45.2k/200k    │
│ 活动会话: 3                                │
│ 平均响应时间: 1.2s                         │
│                                            │
│ 最常用功能:                                │
│ 1. 代码解释 (45%)                         │
│ 2. 错误修复 (30%)                         │
│ 3. 代码生成 (25%)                         │
│                                            │
│ [详细报告] [导出数据] [重置统计]           │
└────────────────────────────────────────────┘
```

## 13. 实现技术细节

### 13.1 Action 实现示例

```kotlin
class NewSessionAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        // 获取当前编辑器上下文
        val editor = e.getData(CommonDataKeys.EDITOR)
        val selectedText = editor?.selectionModel?.selectedText
        
        // 创建新会话
        val sessionService = project.service<ClaudeSessionService>()
        val newSession = sessionService.createSession()
        
        // 如果有选中文本，自动添加为上下文
        if (selectedText != null) {
            newSession.addContext(CodeContext(selectedText))
        }
        
        // 打开工具窗口并切换到新会话
        val toolWindow = ToolWindowManager.getInstance(project)
            .getToolWindow("ClaudeCodePlus")
        toolWindow?.show {
            // 切换到新会话标签
            switchToSession(newSession)
        }
    }
}
```

### 13.2 浮动工具栏实现

```kotlin
class ClaudeFloatingToolbar(
    private val editor: Editor,
    private val project: Project
) : JBPopupMenu() {
    
    init {
        add(createAction("解释代码", ClaudeIcons.Explain) {
            sendToClaudeWithPrompt("请解释这段代码")
        })
        
        add(createAction("优化性能", ClaudeIcons.Optimize) {
            sendToClaudeWithPrompt("请优化这段代码的性能")
        })
        
        addSeparator()
        
        add(createAction("新建会话", AllIcons.General.Add) {
            createNewSession()
        })
    }
    
    private fun showNearSelection() {
        val selectionModel = editor.selectionModel
        if (selectionModel.hasSelection()) {
            val point = editor.visualPositionToXY(
                selectionModel.selectionEndPosition
            )
            show(editor.component, point.x, point.y)
        }
    }
}
```

### 13.3 状态栏 Widget 实现

```kotlin
class ClaudeStatusBarWidget : StatusBarWidget {
    override fun ID() = "ClaudeCodePlus"
    
    override fun getPresentation(): StatusBarWidget.WidgetPresentation {
        return object : StatusBarWidget.TextPresentation {
            override fun getText(): String {
                val usage = getCurrentTokenUsage()
                return "🤖 Claude: ${usage.current}/${usage.max}"
            }
            
            override fun getTooltipText(): String {
                return buildString {
                    appendLine("Claude Code Plus")
                    appendLine("Token 使用: ${getCurrentTokenUsage()}")
                    appendLine("活动会话: ${getActiveSessionCount()}")
                    appendLine("点击查看详情")
                }
            }
            
            override fun getClickConsumer(): Consumer<MouseEvent> {
                return Consumer { showPopupMenu() }
            }
        }
    }
}
```

## 14. 配置和设置

### 14.1 插件设置界面

```
Claude Code Plus 设置
├── 常规
│   ├── ☑ 启用浮动工具栏
│   ├── ☑ 显示状态栏信息
│   ├── ☑ 自动推荐上下文
│   └── ☑ 启用智能提示
├── 快捷键
│   ├── 新建会话: Ctrl+Alt+N [修改]
│   ├── 快速对话: Ctrl+Shift+Space [修改]
│   └── 发送到Claude: Ctrl+Alt+C [修改]
├── 编辑器集成
│   ├── ☑ 显示 Gutter 图标
│   ├── ☑ 启用实时错误检测
│   ├── ☑ 自动修复建议
│   └── 触发延迟: [1000] ms
└── 高级
    ├── 缓存大小: [100] MB
    ├── 历史记录: [1000] 条
    └── ☑ 收集使用统计
```

## 15. 总结

本增强设计方案在保留 toolwindow 核心组件的基础上，充分利用 IntelliJ 平台的能力，提供了：

1. **深度 IDE 集成**：编辑器、调试器、VCS 全方位集成
2. **智能辅助功能**：实时提示、自动推荐、错误检测
3. **高效交互方式**：快捷键、浮动工具栏、快速对话
4. **丰富的 UI 组件**：状态栏、通知、智能气泡
5. **完整的 Action 系统**：菜单、工具栏、Intention Actions

这套方案让 Claude AI 真正融入开发工作流，成为 IDE 的原生功能。