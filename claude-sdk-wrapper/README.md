# Claude SDK Wrapper (Node.js)

这是 Claude Code Plus IntelliJ 插件的 Node.js 服务端包装器，提供 HTTP 和 WebSocket 接口来与 Claude SDK 交互。

使用 nvm 切换至版本 24 后 启动项目

## 安装

```bash
npm install
```

## 运行

### 开发模式（使用 tsx，支持热重载）
```bash
npm run dev
```

### 开发模式（单次运行）
```bash
npm run start:dev
```

### 生产模式（需要先构建）
```bash
npm run build
npm start
```

## 命令行参数

- `--port, -p`: 服务器端口（默认: 18080）
- `--host, -h`: 服务器地址（默认: 127.0.0.1）
- `--help`: 显示帮助信息

示例：
```bash
npm run start:dev -- --port 8080
```

## API 接口

### HTTP 接口

#### 健康检查
- **GET** `/health`
- 返回服务状态信息

#### 流式消息（SSE）
- **POST** `/stream`
- Body:
  ```json
  {
    "message": "你的消息",
    "session_id": "可选的会话ID",
    "new_session": false,
    "options": {
      "cwd": "/path/to/working/directory"
    }
  }
  ```

#### 单次响应
- **POST** `/message`
- Body: 同上

### WebSocket 接口

- **WS** `/ws`
- 支持的命令：
  - `ping`: 心跳检测
  - `message`: 发送消息
  - `health`: 获取健康状态

## 会话管理

- 自动会话超时：2小时
- 支持创建新会话或使用默认会话
- 会话状态自动管理

## 日志

所有日志输出到控制台，格式：
```
2024-01-01 12:00:00 [INFO] Server started on http://127.0.0.1:18080
```