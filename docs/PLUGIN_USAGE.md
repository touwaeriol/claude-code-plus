# Claude Code Plus 插件使用指南

## 前置条件

1. **启动 Claude SDK 服务器**
   ```bash
   cd claude-sdk-wrapper
   python server.py
   ```
   服务器将在端口 18080 上运行

2. **设置 Java 环境**
   ```bash
   export JAVA_HOME=/Users/erio/Library/Java/JavaVirtualMachines/graalvm-ce-17.0.9/Contents/Home
   ```

## 启动插件

使用 Gradle 启动插件：
```bash
./gradlew runIde
```

这将启动一个新的 IntelliJ IDEA 实例，其中包含了 Claude Code Plus 插件。

## 使用插件

1. **打开 Claude Code 工具窗口**
   - 在 IDE 右侧找到 "ClaudeCode" 工具窗口
   - 点击打开

2. **开始对话**
   - 在输入框中输入消息
   - 按 Enter 键发送
   - 等待 Claude 的响应

## 功能特性

- ✅ 实时流式响应
- ✅ 会话管理
  - 默认使用连续会话（保持上下文）
  - 服务器自动管理默认会话
  - "新会话"按钮可开始全新对话
  - 会话超时自动清理（2小时）
  - 自动传入当前项目工作目录
- ✅ 中文界面提示
- ✅ HTTP 通信（无需本地 Python 环境）
- ✅ ANSI 转义序列支持
  - 支持文本颜色（16色、256色、RGB）
  - 支持文本样式（粗体、斜体、下划线、删除线）
  - 支持背景色
  - 自动解析 Claude 返回的带颜色代码输出
- ✅ 工具栏功能
  - 新会话按钮 - 开始新的对话
  - 清空按钮 - 清空聊天记录

## 常见问题

### 1. 无法连接到服务器
- 确保 server.py 已经在端口 18080 上运行
- 检查防火墙设置
- 查看 IDE 日志获取详细错误信息

### 2. Java 版本问题
- 插件需要 Java 17 或更高版本
- 使用 GraalVM 17 已经测试通过

### 3. 插件未显示
- 确保插件已经正确编译
- 检查 plugin.xml 配置
- 重启 IDE

## 开发调试

查看插件日志：
- Help → Show Log in Finder
- 查找包含 "claudecodeplus" 的日志条目

## 测试通信

使用提供的测试脚本验证服务器功能：
```bash
python test_plugin_http.py
```