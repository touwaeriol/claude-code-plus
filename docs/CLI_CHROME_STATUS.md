# Claude Code CLI Chrome 状态查询机制

本文档详细分析了 Claude Code CLI 中 Chrome 扩展集成的状态检测机制，以及如何通过控制请求获取 Chrome 连接状态。

---

## 概述

Claude Code 支持与 Google Chrome 浏览器集成，通过 Chrome 扩展（Claude in Chrome）实现浏览器自动化功能。本文档介绍如何查询 Chrome 扩展的连接状态。

### Chrome 集成架构

```
┌─────────────────────────────────────────────────────────────────┐
│                      Claude Code CLI                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌─────────────────┐    Native Messaging API    ┌─────────────┐ │
│  │  claude-in-     │ <───────────────────────> │   Chrome    │ │
│  │  chrome MCP     │                            │  Extension  │ │
│  └─────────────────┘                            └─────────────┘ │
│         │                                                        │
│         └── 注册为 MCP 服务器                                    │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 1. CLI 内部 Chrome 状态检测函数

### 1.1 `a4A()` - 检测扩展是否安装

此函数通过检查 Native Messaging Host 文件是否存在来判断扩展安装状态。

**代码位置**: `claude-cli-2.0.73.js` 约 line 2759

```javascript
async function a4A() {
  let A = v27();  // 获取 NativeMessagingHosts 目录路径
  if (!A) {
    k(`[Claude in Chrome] Unsupported platform for extension detection: ${kQ()}`);
    return false;
  }

  // 遍历 Chrome profiles 检查扩展文件
  for (let Z of profiles) {
    try {
      let J = path.join(A, S27);  // S27 = "com.anthropic.claude_code_browser_extension.json"
      F09(J);  // 检查文件是否存在
      k(`[Claude in Chrome] Extension ${Y} found in ${Z}`);
      return true;
    } catch {}
  }

  k("[Claude in Chrome] Extension not found in any profile");
  return false;
}
```

### 1.2 `v27()` - 获取 NativeMessagingHosts 目录路径

根据操作系统返回 Chrome NativeMessagingHosts 目录的路径。

```javascript
function v27() {
  let A = kQ();  // 获取平台 (macos/linux/windows)
  let Q = N09(); // 获取用户主目录

  switch (A) {
    case "macos":
      return path.join(Q, "Library", "Application Support", "Google", "Chrome", "NativeMessagingHosts");
    case "linux":
      return path.join(Q, ".config", "google-chrome", "NativeMessagingHosts");
    case "windows": {
      let B = process.env.APPDATA || path.join(Q, "AppData", "Local");
      return path.join(B, "Google", "Chrome", "NativeMessagingHosts");
    }
    default:
      return null;
  }
}
```

### 1.3 相关常量

```javascript
// Native Messaging Host 标识
const HH1 = "com.anthropic.claude_code_browser_extension";

// 配置文件名
const S27 = `${HH1}.json`;

// Windows 注册表路径
const z09 = `HKCU\\Software\\Google\\Chrome\\NativeMessagingHosts\\${HH1}`;

// MCP 服务器名称
const gk = "claude-in-chrome";
```

---

## 2. Chrome 状态的三个维度

| 维度 | 检测方式 | 函数/字段 |
|------|----------|-----------|
| **扩展是否安装** | 检查 NativeMessagingHost 配置文件 | `a4A()` |
| **是否默认启用** | 读取用户配置 | `k1().claudeInChromeDefaultEnabled` |
| **是否已连接** | MCP 服务器状态 | `init.mcp_servers[].status` |

### 2.1 扩展安装检测

通过检查以下路径的文件是否存在：

| 平台 | 路径 |
|------|------|
| macOS | `~/Library/Application Support/Google/Chrome/NativeMessagingHosts/com.anthropic.claude_code_browser_extension.json` |
| Linux | `~/.config/google-chrome/NativeMessagingHosts/com.anthropic.claude_code_browser_extension.json` |
| Windows | `%APPDATA%\Google\Chrome\NativeMessagingHosts\com.anthropic.claude_code_browser_extension.json` 或注册表 |

### 2.2 默认启用配置

```javascript
// 检查是否默认启用
function e7() {
  // 环境变量优先
  if (ZI(process.env.CLAUDE_CODE_ENABLE_CFC)) return true;
  if (ZI(process.env.CLAUDE_CODE_ENABLE_CFC) === false) return false;

  // 读取用户配置
  let Q = k1();  // 获取配置对象
  if (Q.claudeInChromeDefaultEnabled !== undefined) {
    return Q.claudeInChromeDefaultEnabled;
  }

  return false;
}
```

### 2.3 MCP 连接状态

在 `init` 系统消息中返回 MCP 服务器状态：

```javascript
yield {
  type: "system",
  subtype: "init",
  mcp_servers: Y.map(b1 => ({
    name: b1.name,      // "claude-in-chrome"
    status: b1.type     // "connected" | "failed" | "pending" | "needs-auth"
  })),
  // ...
}
```

---

## 3. `/chrome` 斜杠命令 UI

CLI 提供 `/chrome` 命令用于管理 Chrome 集成。

### 3.1 UI 组件 (TJ7)

```javascript
function TJ7({
  onDone: A,
  isExtensionInstalled: Q,      // 扩展是否安装
  configEnabled: B,              // 是否默认启用
  isClaudeAISubscriber: G        // 是否订阅用户
}) {
  // 渲染 Chrome 状态 UI
  // - 显示安装状态
  // - 提供启用/禁用开关
  // - 显示权限管理链接
}

// 调用入口
async function PJ7(A) {
  let Q = await a4A();           // 检测扩展安装状态
  let B = k1();                  // 获取配置
  let G = VB();                  // 是否订阅用户

  return createElement(TJ7, {
    onDone: A,
    isExtensionInstalled: Q,
    configEnabled: B.claudeInChromeDefaultEnabled,
    isClaudeAISubscriber: G
  });
}
```

### 3.2 菜单选项

```javascript
// /chrome 命令提供的选项
const options = [
  { label: "Manage permissions", value: "manage-permissions" },
  { label: "Toggle default enabled", value: "toggle-default" },
  { label: "Reconnect extension", value: "reconnect" }
];
```

---

## 4. 扩展控制请求：`chrome_status`

### 4.1 请求格式

```json
{
  "type": "control_request",
  "request_id": "req_chrome_1",
  "request": {
    "subtype": "chrome_status"
  }
}
```

### 4.2 响应格式

```json
{
  "type": "control_response",
  "response": {
    "subtype": "success",
    "request_id": "req_chrome_1",
    "response": {
      "installed": true,
      "enabled": true,
      "connected": true,
      "mcpServerStatus": "connected",
      "extensionVersion": "1.0.36"
    }
  }
}
```

### 4.3 响应字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| `installed` | boolean | 扩展是否已安装（Native Messaging Host 存在） |
| `enabled` | boolean | 是否默认启用 Chrome 集成 |
| `connected` | boolean | MCP 服务器是否已连接 |
| `mcpServerStatus` | string | MCP 状态: `"connected"` / `"failed"` / `"pending"` / `"needs-auth"` / `null` |
| `extensionVersion` | string? | 扩展版本（如果已连接） |

---

## 5. 补丁实现

### 5.1 补丁架构

```
┌─────────────────────────────────────────────────────────────────────────┐
│                     AST 补丁应用流程                                      │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  Step 1: 找到控制请求处理位置                                             │
│  ┌───────────────────────────────────────────────────────────────────┐ │
│  │  // 查找: if (d.request.subtype === "interrupt")                  │ │
│  │  // 在此 if-else 链中添加 chrome_status 分支                        │ │
│  └───────────────────────────────────────────────────────────────────┘ │
│                                                                         │
│  Step 2: 添加 chrome_status 处理逻辑                                     │
│  ┌───────────────────────────────────────────────────────────────────┐ │
│  │  if (d.request.subtype === "chrome_status") {                     │ │
│  │    const installed = await a4A();                                 │ │
│  │    const config = k1();                                           │ │
│  │    const mcpStatus = getMcpServerStatus("claude-in-chrome");      │ │
│  │    s(d, {                                                         │ │
│  │      installed: installed,                                        │ │
│  │      enabled: config.claudeInChromeDefaultEnabled ?? false,       │ │
│  │      connected: mcpStatus?.status === "connected",                │ │
│  │      mcpServerStatus: mcpStatus?.status ?? null,                  │ │
│  │      extensionVersion: mcpStatus?.serverInfo?.version ?? null     │ │
│  │    });                                                            │ │
│  │  }                                                                │ │
│  └───────────────────────────────────────────────────────────────────┘ │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### 5.2 补丁文件

补丁文件: `claude-agent-sdk/cli-patches/patches/002-chrome-status.js`

---

## 6. SDK 使用示例

### 6.1 Kotlin SDK

```kotlin
// ControlProtocol.kt
suspend fun getChromeStatus(): ChromeStatus {
    val request = buildJsonObject {
        put("subtype", "chrome_status")
    }

    val response = sendControlRequestInternal(request)
    val data = response.response

    return ChromeStatus(
        installed = data["installed"]?.jsonPrimitive?.boolean ?: false,
        enabled = data["enabled"]?.jsonPrimitive?.boolean ?: false,
        connected = data["connected"]?.jsonPrimitive?.boolean ?: false,
        mcpServerStatus = data["mcpServerStatus"]?.jsonPrimitive?.contentOrNull,
        extensionVersion = data["extensionVersion"]?.jsonPrimitive?.contentOrNull
    )
}

data class ChromeStatus(
    val installed: Boolean,
    val enabled: Boolean,
    val connected: Boolean,
    val mcpServerStatus: String?,
    val extensionVersion: String?
)
```

### 6.2 使用示例

```kotlin
val chromeStatus = controlProtocol.getChromeStatus()

when {
    !chromeStatus.installed -> {
        println("Chrome 扩展未安装，请访问 https://claude.ai/chrome 安装")
    }
    !chromeStatus.connected -> {
        println("Chrome 扩展未连接，状态: ${chromeStatus.mcpServerStatus}")
    }
    else -> {
        println("Chrome 已连接，版本: ${chromeStatus.extensionVersion}")
    }
}
```

---

## 7. 相关 URL

| 用途 | URL |
|------|-----|
| 安装扩展 | https://claude.ai/chrome |
| 管理权限 | https://clau.de/chrome/permissions |
| 重新连接 | https://clau.de/chrome/reconnect |

---

## 8. 参考文档

- [CLI 后台执行机制](./CLI_BACKGROUND_EXECUTION.md) - 控制请求协议基础
- [SDK 数据流架构](./DATA_FLOW_ARCHITECTURE.md) - SDK 与 CLI 通信架构
- [官方 Chrome 集成文档](https://code.claude.com/docs/en/chrome) - 官方使用指南
