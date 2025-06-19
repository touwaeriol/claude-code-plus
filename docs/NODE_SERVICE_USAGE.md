# Node.js 服务使用说明

## 概述

Claude Code Plus 插件包含一个基于 Node.js 的后端服务，用于与 Claude SDK 进行通信。该服务需要手动启动。

## 系统要求

- Node.js 18.0.0 或更高版本
- macOS、Linux 或 Windows 系统

## 启动服务

### 方法一：使用启动脚本（推荐）

在项目根目录运行：

```bash
./start-node-service.sh
```

### 方法二：手动启动

1. 进入服务目录：
   ```bash
   cd src/main/resources/claude-node
   ```

2. 启动服务：
   ```bash
   node server-esm-wrapper.mjs --port 18080 --host 127.0.0.1
   ```

## 验证服务状态

服务启动后，可以通过以下方式验证：

1. **健康检查端点**：
   ```bash
   curl http://127.0.0.1:18080/health
   ```
   应返回：`{"status":"ok","timestamp":"..."}`

2. **查看日志输出**：
   服务启动后会在控制台输出：
   ```
   Claude SDK Wrapper Server started on http://127.0.0.1:18080
   Available endpoints:
     POST /stream  - Stream messages with Server-Sent Events
     POST /message - Single request/response
     GET  /health  - Health check
     WS   /ws      - WebSocket connection
   ```

## 服务端口

默认服务端口为 `18080`，如需修改，可以在启动时指定：

```bash
node server-esm-wrapper.mjs --port 8080 --host 127.0.0.1
```

## 停止服务

按 `Ctrl+C` 停止服务。

## 故障排除

### 1. Node.js 版本错误
错误信息：`需要 Node.js 18.0.0 或更高版本`

解决方法：
- 检查 Node.js 版本：`node --version`
- 升级 Node.js 到 18+ 版本

### 2. 服务文件不存在
错误信息：`服务文件不存在`

解决方法：
- 运行构建命令：`./gradlew buildNodeService`
- 确保在项目根目录执行

### 3. 端口被占用
错误信息：`EADDRINUSE: address already in use`

解决方法：
- 检查端口占用：`lsof -i :18080`
- 使用其他端口或停止占用进程

### 4. 连接被拒绝
插件提示：`无法连接到 Claude SDK 服务`

解决方法：
- 确保服务已启动
- 检查防火墙设置
- 验证服务地址和端口正确

## 开发模式

如果需要在开发模式下运行（带有实时重载）：

1. 进入 wrapper 目录：
   ```bash
   cd claude-sdk-wrapper
   ```

2. 启动开发服务器：
   ```bash
   npm run dev
   ```

## 配置说明

服务支持以下命令行参数：

- `--port`: 服务端口（默认：18080）
- `--host`: 服务地址（默认：127.0.0.1）
- `--help`: 显示帮助信息

## 注意事项

1. 服务必须在使用插件前启动
2. 插件会自动检测服务状态并提示
3. 服务日志会输出到控制台
4. 确保 API Key 已正确配置

## 技术架构

- **运行时**: Node.js 18+
- **框架**: Express + WebSocket
- **SDK**: @anthropic-ai/claude-code
- **通信协议**: HTTP/WebSocket/SSE