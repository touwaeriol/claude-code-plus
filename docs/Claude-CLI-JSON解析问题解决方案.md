# Claude CLI JSON解析问题解决方案

## 问题描述

在使用ClaudeCliWrapper调用Claude CLI时，出现JSON解析异常：

```
com.fasterxml.jackson.core.JsonParseException: Illegal character ((CTRL-CHAR, code 27)): only regular white space (\r, \n, \t) is allowed between tokens
```

## 问题原因分析

### 1. ANSI转义序列混入输出
Claude CLI输出包含终端控制字符：
- `\u001B]0;claude\u0007` - 设置终端窗口标题为"claude"
- 这个序列的作用是提升用户体验，让用户在任务栏看到"claude"标识

### 2. JSON解析器行为
JSON解析器期望严格的JSON格式：
- 必须以有效JSON字符开始（`{`, `[`, `"`, 数字等）
- 控制字符（如ESC，ASCII 27）不能出现在JSON token之间
- 只允许合法空白字符（`\r`, `\n`, `\t`, 空格）

### 3. 实际输出格式
```
\u001B]0;claude\u0007{"type":"system","subtype":"init",...}
```
解析器在第一个字符`\u001B`就失败，因为这不是有效的JSON开始字符。

## 解决方案

### 方案一：添加缺失的输出格式参数（推荐）
**根本原因**：ClaudeCliWrapper缺少`--output-format stream-json`参数

**修复**：
```kotlin
// 修改前
args.addAll(listOf("--print", prompt.trim()))

// 修改后  
args.addAll(listOf("--print", "--output-format", "stream-json", prompt.trim()))
```

**优点**：
- 从源头解决问题
- Claude CLI输出纯净的JSON流
- 不需要额外的字符串处理
- 符合Claude CLI的设计意图

### 方案二：ANSI序列清理（备选）
如果无法使用`stream-json`格式，可以预处理输入：

```kotlin
// 清理ANSI转义序列
val cleanLine = line.replace(Regex("\\u001B\\[[;\\d]*m|\\u001B\\]0;.*?\\u0007"), "").trim()

// 跳过非JSON行
if (!cleanLine.startsWith("{")) {
    return@forEach
}
```

## 文件修改记录

### 修改文件
- `cli-wrapper/src/main/kotlin/com/claudecodeplus/sdk/ClaudeCliWrapper.kt`

### 修改内容
1. **添加输出格式参数**（第118行）：
   ```kotlin
   args.addAll(listOf("--print", "--output-format", "stream-json", prompt.trim()))
   ```

2. **简化JSON解析逻辑**（第157-158行）：
   ```kotlin
   // 直接解析JSON，无需预处理
   val jsonNode = objectMapper.readTree(line)
   ```

## 验证方法

### 测试ANSI序列影响
```bash
# 创建包含ANSI序列的测试文件
echo -e '\u001B]0;claude\u0007{"test": "value"}' > test.json

# 测试JSON解析器行为
python3 -c "import json; print(json.loads(open('test.json').read()))"
# 结果：JSONDecodeError: Expecting value: line 1 column 1 (char 0)
```

### 测试修复效果
运行修复后的ClaudeCliWrapper，应该能正常解析JSON输出而不出现控制字符相关异常。

## 相关知识

### ANSI转义序列格式
- `\u001B` - ESC字符（ASCII 27）
- `]0;` - OSC（Operating System Command）设置窗口标题
- `text` - 标题内容
- `\u0007` - BEL字符（ASCII 7）作为终止符

### Claude CLI输出格式选项
- `text`（默认）- 混合终端控制序列的文本输出
- `json` - 单次JSON结果
- `stream-json` - 实时流式JSON输出（推荐用于编程集成）

## 总结

这次问题的核心在于ClaudeCliWrapper的实现与其设计文档不符：
- 文档中明确说明应使用`--output-format stream-json`
- 实际代码中缺少了这个关键参数
- 导致接收到混合了终端控制序列的输出

通过添加正确的输出格式参数，从源头解决了JSON解析问题，这比后处理清理ANSI序列的方案更加优雅和可靠。