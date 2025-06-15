# Claude Code Plus 日志格式说明

## 概述

Claude Code Plus 插件会记录所有与服务器的交互，包括请求、响应和 WebSocket 消息。日志文件保存在项目根目录下的 `logs` 目录中。

## 日志文件位置

日志文件保存在**项目根目录**下的 `logs` 目录中：
- 项目目录: `<your-project-path>/logs/`
- 例如: `/Users/username/projects/myproject/logs/`

## 日志文件命名

格式：`session_yyyy-MM-dd_HH-mm-ss_序号.log`

示例：`session_2025-06-15_18-30-00_1.log`

## 日志内容格式

### 1. ANSI 转义序列

日志会**原样保留** ANSI 转义序列，例如：

```
[RESPONSE CHUNK] 2025-06-15 18:30:06.789
Type: text
Content Length: 150 chars
Content:
找到以下文件：
- \033[1;32mREADME.md\033[0m
- \033[1;33msrc/main.py\033[0m
- \033[1;34mtests/\033[0m
Detected formats: ANSI
```

这样可以：
- 在支持 ANSI 的终端中查看时显示颜色
- 分析 Claude 返回的格式化输出
- 调试 ANSI 解析器的实现

### 2. Markdown 格式

Markdown 内容同样会**完整保留**，包括：

```
[RESPONSE CHUNK] 2025-06-15 18:30:07.123
Type: text
Content Length: 500 chars
Content:
# 项目结构

这是一个 **Python** 项目，包含以下文件：

```python
def hello_world():
    print("Hello, World!")
```

## 主要功能
- 支持多种格式
- 易于扩展
- [查看文档](https://example.com)

Detected formats: Markdown
```

### 3. 混合格式

如果响应同时包含 ANSI 和 Markdown，日志会标注：

```
[RESPONSE CHUNK] 2025-06-15 18:30:08.456
Type: text
Content Length: 300 chars
Content:
\033[1;36m## 代码示例\033[0m

```python
print("\033[32mGreen text\033[0m")
```

Detected formats: ANSI, Markdown
```

## 查看日志的方法

### 1. 使用插件内置功能

通过菜单 **Tools → Show Claude Code Logs** 查看

### 2. 使用终端查看（保留 ANSI 颜色）

```bash
# Mac/Linux
cat logs/session_*.log | less -R

# 或使用 bat（如果安装了）
bat logs/session_*.log
```

### 3. 使用文本编辑器

任何文本编辑器都可以打开日志文件，但 ANSI 颜色可能不会显示。

### 4. 使用提供的测试脚本

```bash
python test_log_formats.py
```

这个脚本会：
- 找到最新的日志文件
- 统计 ANSI 序列和 Markdown 元素
- 显示日志预览和统计信息

## 日志分析示例

### 查找所有包含错误的日志

```bash
grep -n "Error:" logs/*.log
```

### 提取所有 ANSI 彩色输出

```bash
grep -E "\\033\[" logs/*.log
```

### 查看特定时间段的日志

```bash
ls -la logs/session_2025-06-15*.log
```

## 日志清理

插件会自动保留最近 50 个日志文件。如需手动清理：

```bash
# 删除 30 天前的日志
find logs -name "*.log" -mtime +30 -delete
```

## 隐私说明

日志文件包含：
- 您发送给 Claude 的所有消息
- Claude 的所有响应
- 文件路径和项目结构信息

请妥善保管这些日志文件，避免泄露敏感信息。