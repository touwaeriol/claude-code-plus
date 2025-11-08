# 故障排除指南

## IDEA 插件工具窗口显示空白

### 症状
在 IDEA 中打开 Claude Code Plus 工具窗口时显示空白页面。

### 诊断步骤

#### 1. 检查日志
查看 `runide.log` 文件或 IDE 日志，查找关键信息：
```
✅ HTTP Server started at: http://127.0.0.1:xxxxx
✅ Page loaded with status: 200
❌ Stopping HTTP server (如果看到这个，说明服务器过早停止)
```

#### 2. 检查前端构建
确认前端资源已正确构建：
```bash
ls jetbrains-plugin/src/main/resources/frontend/
# 应该看到: index.html, assets/index.js
```

### 解决方案

#### 方案 1：完全重新构建（推荐）
```bash
# Windows PowerShell
cd frontend
npm install
npm run build
cd ..
.\gradlew clean runIde

# Linux/Mac
cd frontend && npm install && npm run build && cd ..
./gradlew clean runIde
```

#### 方案 2：检查 JCEF 支持
确认您的 IntelliJ IDEA 版本支持 JCEF（2024.3+）：
- 打开 Help → About
- 确认版本 ≥ 2024.3 (Build 243+)

#### 方案 3：清理临时目录
JCEF 和 HTTP 服务器使用临时目录，可能被锁定或损坏：
```bash
# Windows PowerShell
Get-ChildItem $env:TEMP | Where-Object { $_.Name -like 'claude-frontend-*' } | Remove-Item -Recurse -Force

# Linux/Mac
rm -rf /tmp/claude-frontend-*
```

#### 方案 4：切换到 Compose UI 版本
如果 JCEF 版本持续有问题，可以切换到 Compose UI 版本。

编辑 `jetbrains-plugin/src/main/resources/META-INF/plugin.xml`：
```xml
<!-- 注释掉 Vue 版本 -->
<!--
<toolWindow id="Claude Code Plus"
            anchor="right"
            icon="/icons/claude-code-simple.svg"
            factoryClass="com.claudecodeplus.toolwindow.VueToolWindowFactory"/>
-->

<!-- 启用 Compose 版本 -->
<toolWindow id="Claude Code Plus"
            anchor="right"
            icon="/icons/claude-code-simple.svg"
            factoryClass="com.claudecodeplus.plugin.ClaudeCodePlusToolWindowFactory"/>
```

然后重新构建：
```bash
.\gradlew clean runIde   # Windows
./gradlew clean runIde   # Linux/Mac
```

### 常见错误代码

#### 错误 1: "HTTP Server not started"
**原因**：HttpServerProjectService 初始化失败  
**解决**：
1. 检查端口是否被占用（查看日志中的端口号）
2. 尝试关闭其他占用端口的程序
3. 重启 IDE

#### 错误 2: "Frontend resources not found in JAR"
**原因**：前端未构建或构建失败  
**解决**：
```bash
cd frontend
npm install
npm run build
```

#### 错误 3: "JCEF is not supported"
**原因**：IDE 版本过低或 JCEF 未启用  
**解决**：
1. 升级到 IntelliJ IDEA 2024.3+
2. 或使用 Compose UI 版本（见方案 4）

#### 错误 4: 页面加载后立即空白
**原因**：HTTP 服务器过早停止或 Vue 应用初始化失败  
**可能原因**：
- 前端资源不完整
- JavaScript 错误
- 浏览器扩展冲突（如果在外部浏览器测试）

**解决**：
1. 完全重新构建（见方案 1）
2. 检查构建产物大小：`assets/index.js` 应该约 11MB
3. 测试外部浏览器访问（从日志找到 URL）

### 调试技巧

#### 1. 启用详细日志
在 Help → Diagnostic Tools → Debug Log Settings 中添加：
```
com.claudecodeplus
```

然后重启 IDE 并重现问题，日志将包含更多详细信息。

#### 2. 使用浏览器开发者工具
如果 JCEF 支持开发者工具（某些版本可用）：
- 在 JCEF 窗口中右键 → Inspect Element
- 查看 Console 错误
- 查看 Network 请求
- 确认 Vue 应用是否挂载到 `#app`

#### 3. 测试 HTTP 服务器
从日志中找到服务器 URL（如 `http://127.0.0.1:54531`），在**外部浏览器**中访问：
- ✅ 如果外部浏览器正常显示 → JCEF 问题，尝试方案 4
- ❌ 如果外部浏览器也空白 → 前端构建问题，执行方案 1

#### 4. 检查构建产物
```bash
# 列出前端文件
ls -lh jetbrains-plugin/src/main/resources/frontend/assets/

# Windows
dir jetbrains-plugin\src\main\resources\frontend\assets\

# 确认 index.js 大小约 11MB
# 如果文件不存在或大小异常，重新构建前端
```

---

## 浏览器扩展冲突问题

### 症状
在**外部浏览器**中访问前端界面时出现以下错误：
```
Uncaught (in promise) TypeError: Cannot read properties of undefined (reading 'showAI')
n.component is not a function
Unchecked runtime.lastError: The message port closed before a response was received.
```

### 原因
某些浏览器扩展（特别是 AI 助手类扩展如 Claude、ChatGPT、Copilot 等）会向所有网页注入脚本，这些脚本可能会干扰 Vue.js 的正常运行。

### 解决方案

#### 方案 1：使用隐身模式（最快）
1. 打开浏览器的隐身/无痕模式：
   - Chrome: `Ctrl+Shift+N` (Windows) 或 `Cmd+Shift+N` (Mac)
   - Firefox: `Ctrl+Shift+P` (Windows) 或 `Cmd+Shift+P` (Mac)
2. 在隐身模式下访问前端 URL
3. 隐身模式默认禁用所有扩展，避免冲突

#### 方案 2：临时禁用扩展
1. 打开 Chrome 扩展管理页面：`chrome://extensions/`
2. 找到以下可能冲突的扩展：
   - Claude、ChatGPT、Copilot 等 AI 助手
   - 页面修改类扩展（广告屏蔽、脚本注入等）
3. 临时禁用这些扩展
4. 刷新页面

#### 方案 3：直接使用 IDEA 内置窗口
不要在外部浏览器测试，直接在 IDEA 的工具窗口中使用（JCEF 不受浏览器扩展影响）。

### 验证扩展冲突

打开浏览器开发者工具（F12），查看 Sources 标签：
- 如果看到 `content.js`、`injected.js` 等文件 → 有扩展注入了脚本
- 检查 Console 中错误的来源文件名

---

## 前端开发模式

如果需要调试前端，建议使用独立开发服务器：

```bash
cd frontend
npm run dev
```

然后访问 `http://localhost:5173`

### 注意事项
- 开发模式下前端运行在独立服务器，与插件解耦
- 需要配置 API 代理以连接后端
- 生产模式下前端会被构建到插件资源目录并嵌入 JCEF

---

## 其他常见问题

### 问题：Gradle 构建失败
**症状**：`./gradlew runIde` 报错  
**解决方案**：
```bash
# 清理并重试
.\gradlew clean
.\gradlew --refresh-dependencies runIde

# 如果仍然失败，检查 JDK 版本
java -version  # 应该是 JDK 17 或 21
```

### 问题：前端依赖安装失败
**症状**：`npm install` 报错  
**解决方案**：
```bash
cd frontend
rm -rf node_modules package-lock.json
npm install

# 或使用国内镜像
npm install --registry=https://registry.npmmirror.com
```

### 问题：Claude CLI 未找到
**症状**：插件提示 "Claude CLI is not installed"  
**解决方案**：
1. 安装 Claude CLI：
   ```bash
   # macOS/Linux
   curl -sS https://claude.ai/cli/install.sh | sh
   
   # Windows
   # 访问 https://claude.ai/cli 下载安装器
   ```
2. 配置 API 密钥：
   ```bash
   claude auth login
   ```
3. 重启 IDE

### 问题：端口被占用
**症状**：日志显示 "Address already in use"  
**解决方案**：
```bash
# Windows - 查找占用端口的进程
netstat -ano | findstr :54531
taskkill /PID <进程ID> /F

# Linux/Mac
lsof -ti:54531 | xargs kill -9
```

### 问题：会话状态丢失
**症状**：切换窗口后会话历史消失  
**说明**：这是设计行为，会话状态在项目级服务中管理。如需持久化，请使用导出功能。

---

## 获取帮助

如果以上方案都无法解决问题：

1. **收集诊断信息**：
   - 完整的 `runide.log` 文件
   - IDE 版本（Help → About）
   - 操作系统版本
   - 错误截图

2. **提交 Issue**：
   - 访问 https://github.com/touwaeriol/claude-code-plus/issues
   - 使用 Bug Report 模板
   - 附上收集的诊断信息

3. **社区支持**：
   - 查看已有 Issues
   - 参考项目文档

---

## 快速诊断清单

遇到问题时，按顺序检查：

- [ ] 前端已构建？（`ls jetbrains-plugin/src/main/resources/frontend/`）
- [ ] Gradle 构建成功？（无错误输出）
- [ ] JCEF 支持？（IntelliJ IDEA 2024.3+）
- [ ] HTTP 服务器启动？（查看日志）
- [ ] 临时目录权限正常？（可读写 `%TEMP%` 或 `/tmp`）
- [ ] 端口未被占用？（查看日志中的端口号）
- [ ] Claude CLI 已安装？（`claude --version`）
- [ ] 日志中无严重错误？（搜索 "ERROR" 或 "SEVERE"）

如果所有检查都通过但仍有问题，尝试**完全重新构建**（方案 1）。








