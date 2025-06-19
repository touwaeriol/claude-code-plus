console.log('Starting server...');

import express from 'express';
import cors from 'cors';
import { WebSocketServer } from 'ws';
import http from 'http';
import { v4 as uuidv4 } from 'uuid';
import winston from 'winston';
import yargs from 'yargs';
import { hideBin } from 'yargs/helpers';
import { ClaudeService } from './services/claudeService';
import { SessionManager } from './services/sessionManager';
import { setupHttpRoutes } from './routes/httpRoutes';
import { setupWebSocketHandlers } from './routes/wsHandlers';

console.log('Imports completed');

// 配置日志
const logger = winston.createLogger({
  level: 'info',
  format: winston.format.combine(
    winston.format.timestamp(),
    winston.format.printf(({ timestamp, level, message }) => {
      return `${timestamp} [${level.toUpperCase()}] ${message}`;
    })
  ),
  transports: [
    new winston.transports.Console()
  ]
});

// 解析命令行参数
const argv = yargs(hideBin(process.argv))
  .option('port', {
    alias: 'p',
    type: 'number',
    description: '服务器端口',
    default: 18080
  })
  .option('host', {
    alias: 'h',
    type: 'string',
    description: '服务器地址',
    default: '127.0.0.1'
  })
  .help()
  .parseSync();

// 创建 Express 应用
const app = express();
const server = http.createServer(app);

// 配置中间件
app.use(cors());
app.use(express.json({ limit: '10mb' }));
app.use(express.urlencoded({ extended: true, limit: '10mb' }));

// 请求日志中间件
app.use((req, res, next) => {
  logger.info(`${req.method} ${req.path}`);
  next();
});

// 创建服务实例
const sessionManager = new SessionManager(logger);
const claudeService = new ClaudeService(logger, sessionManager);

// 设置 HTTP 路由
setupHttpRoutes(app, claudeService, logger);

// 创建 WebSocket 服务器
const wss = new WebSocketServer({ server });

// 设置 WebSocket 处理
setupWebSocketHandlers(wss, claudeService, logger);

// 启动服务器
server.listen(argv.port, argv.host, () => {
  logger.info(`Claude SDK Wrapper Server started on http://${argv.host}:${argv.port}`);
  logger.info('Available endpoints:');
  logger.info('  POST /stream  - Stream messages with Server-Sent Events');
  logger.info('  POST /message - Single request/response');
  logger.info('  GET  /health  - Health check');
  logger.info('  WS   /ws      - WebSocket connection');
});

// 优雅关闭
process.on('SIGINT', () => {
  logger.info('Shutting down server...');
  server.close(() => {
    logger.info('Server closed');
    process.exit(0);
  });
});

export { logger };