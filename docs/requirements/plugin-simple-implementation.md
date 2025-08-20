# Claude Code Plus IntelliJ 插件简化实现方案

## 1. 核心目标

将 toolwindow 模块嵌入 IntelliJ IDEA，利用 IDE 的文件索引服务增强上下文引用能力。

## 2. 最小可行产品（MVP）功能

### 2.1 必须实现的功能

#### 工具窗口集成
```kotlin
class ClaudeCodePlusToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // 1. 创建 toolwindow 的 ChatViewNew
        val chatView = PluginComposeFactory.createComposePanel(
            unifiedSessionService = unifiedSessionService,
            sessionManager = sessionManager,
            workingDirectory = project.basePath,
            project = project,
            fileIndexService = IdeaFileIndexService(project), // ← 使用 IDEA 文件索引
            projectService = IdeaProjectServiceAdapter(project)
        )
        
        // 2. 直接添加到工具窗口，不做额外包装
        val content = ContentFactory.getInstance()
            .createContent(chatView, "", false)
        toolWindow.contentManager.addContent(content)
    }
}
```

#### IDEA 文件索引服务实现
```kotlin
class IdeaFileIndexService(private val project: Project) : FileIndexService {
    
    override fun searchFiles(pattern: String): List<FileInfo> {
        val result = mutableListOf<FileInfo>()
        
        // 使用 IDEA 的 FilenameIndex
        val files = FilenameIndex.getFilesByName(
            project, 
            pattern, 
            GlobalSearchScope.projectScope(project)
        )
        
        files.forEach { virtualFile ->
            result.add(FileInfo(
                path = virtualFile.path,
                name = virtualFile.name,
                size = virtualFile.length
            ))
        }
        
        return result
    }
    
    override fun getFileContent(path: String): String? {
        val virtualFile = LocalFileSystem.getInstance().findFileByPath(path)
        return virtualFile?.let { 
            String(it.contentsToByteArray())
        }
    }
    
    override fun getRecentFiles(): List<String> {
        // 使用 IDEA 的最近文件管理器
        return RecentProjectsManager.getInstance()
            .getRecentFiles(project)
            .map { it.path }
    }
}
```

### 2.2 基本配置

#### plugin.xml
```xml
<idea-plugin>
    <id>com.claudecodeplus</id>
    <name>Claude Code Plus</name>
    
    <depends>com.intellij.modules.platform</depends>
    
    <extensions defaultExtensionNs="com.intellij">
        <!-- 工具窗口 -->
        <toolWindow id="ClaudeCodePlus" 
                    anchor="right" 
                    factoryClass="com.claudecodeplus.plugin.ClaudeCodePlusToolWindowFactory"/>
    </extensions>
    
    <actions>
        <!-- 基本的打开/关闭动作 -->
        <action id="ClaudeCodePlus.Toggle" 
                class="com.claudecodeplus.plugin.actions.ToggleToolWindowAction"
                text="Toggle Claude Code Plus">
            <keyboard-shortcut keymap="$default" first-keystroke="ctrl alt C"/>
        </action>
    </actions>
</idea-plugin>
```

## 3. 架构设计

### 3.1 模块关系
```
jetbrains-plugin
├── 依赖 toolwindow（UI组件）
├── 依赖 cli-wrapper（Claude SDK）
└── 实现 FileIndexService（使用 IDEA API）
```

### 3.2 服务适配
```kotlin
// 项目服务适配器
class IdeaProjectServiceAdapter(private val project: Project) : ProjectService {
    override fun getProjectPath(): String = project.basePath ?: ""
    override fun getProjectName(): String = project.name
}

// 文件搜索服务
class IdeaFileSearchService(private val project: Project) : FileSearchService {
    override fun searchInFiles(query: String): List<SearchResult> {
        // 使用 IDEA 的 FindManager
        val findManager = FindManager.getInstance(project)
        // 实现搜索逻辑
    }
}
```

## 4. 用户界面

### 4.1 工具窗口布局
```
┌──────────────────────────────────────────────────┐
│              Claude Code Plus                    │
├──────────────────────────────────────────────────┤
│                                                  │
│         ChatViewNew (from toolwindow)            │
│                                                  │
│  包含：                                          │
│  - 消息列表（用户/AI/工具调用）                 │
│  - 输入区域（支持 @ 引用）                      │
│  - 模型选择、发送按钮等                         │
│                                                  │
└──────────────────────────────────────────────────┘
```

### 4.2 文件选择器增强
当用户在输入框中输入 `@` 或点击"添加上下文"时：
```
┌─────────────────────────────────────┐
│ 选择文件                            │
├─────────────────────────────────────┤
│ 🔍 搜索项目文件...                  │
├─────────────────────────────────────┤
│ 最近文件：                          │
│ • UserService.java (5分钟前)        │
│ • application.yml (1小时前)         │
├─────────────────────────────────────┤
│ 项目文件：                          │
│ 📁 src/                             │
│   📁 main/                          │
│     📁 java/                        │
│       📄 Main.java                  │
└─────────────────────────────────────┘
```

## 5. 实现步骤

### 第 1 步：基础集成
1. 创建 ToolWindowFactory
2. 集成 toolwindow 的 ChatViewNew
3. 确保基本聊天功能工作

### 第 2 步：文件索引服务
1. 实现 IdeaFileIndexService
2. 连接到 toolwindow 的上下文选择器
3. 测试文件搜索和加载

### 第 3 步：基本交互
1. 添加快捷键（Ctrl+Alt+C）
2. 添加 View 菜单项
3. 确保主题适配

## 6. 技术要点

### 6.1 依赖配置
```kotlin
// build.gradle.kts
dependencies {
    implementation(project(":toolwindow")) {
        exclude(group = "org.jetbrains.kotlinx")
    }
    implementation(project(":cli-wrapper")) {
        exclude(group = "org.jetbrains.kotlinx")
    }
    
    intellijPlatform {
        intellijIdeaCommunity("2025.1")
    }
}
```

### 6.2 关键 API 使用
```kotlin
// 获取项目文件
val projectFiles = ProjectRootManager.getInstance(project)
    .contentRoots

// 使用 PSI 解析代码
val psiFile = PsiManager.getInstance(project)
    .findFile(virtualFile)

// 获取文件历史
val history = LocalHistory.getInstance()
    .getByteContent(virtualFile)
```

## 7. 测试要点

### 7.1 功能测试
- ✅ 工具窗口能打开/关闭
- ✅ 聊天功能正常（发送消息、接收响应）
- ✅ 文件搜索能找到项目文件
- ✅ @ 引用能触发文件选择器
- ✅ 选中的文件能作为上下文发送

### 7.2 性能测试
- 文件索引不应阻塞 UI
- 大项目（>10000文件）搜索响应 < 1秒
- 内存使用合理

### 7.3 兼容性测试
- IntelliJ IDEA 2022.3+
- Light/Dark 主题
- Windows/Mac/Linux

## 8. 后续增强（非当前版本）

**P1 - 下一版本**
- 编辑器右键菜单："发送到 Claude"
- 选中代码自动填充到输入框
- 状态栏显示 Claude 状态

**P2 - 未来版本**
- Git 集成
- 调试器变量分析
- 代码 Intention Actions

## 9. 风险和限制

### 9.1 已知限制
- 依赖 Claude CLI 必须预先安装
- Compose UI 在某些 IDE 版本可能有兼容性问题
- 文件索引仅限于项目范围

### 9.2 解决方案
- 提供 Claude CLI 安装引导
- 使用 Jewel UI 确保兼容性
- 后续可扩展到全局文件搜索

## 10. 成功标准

**核心功能完成**：
1. 工具窗口正常显示 toolwindow 的聊天界面
2. 能够发送消息和接收 AI 响应
3. 文件引用使用 IDEA 的文件系统
4. 基本的快捷键和菜单集成

**用户体验**：
- 与 IDE 主题一致
- 响应速度快
- 无崩溃和错误