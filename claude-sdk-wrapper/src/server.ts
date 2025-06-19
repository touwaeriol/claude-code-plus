import winston from 'winston';
import { ClaudeService } from './services/claudeService';
import { SessionManager } from './services/sessionManager';
import { UnixSocketService } from './services/unixSocketService';

// 配置日志 - 减少日志输出，避免干扰 socket 路径输出
const logger = winston.createLogger({
  level: 'error', // 只输出错误
  format: winston.format.combine(
    winston.format.timestamp(),
    winston.format.printf(({ timestamp, level, message }) => {
      return `${timestamp} [${level.toUpperCase()}] ${message}`;
    })
  ),
  transports: [
    new winston.transports.Console() // 使用默认配置
  ]
});

// 创建服务实例
const sessionManager = new SessionManager(logger);
const claudeService = new ClaudeService(logger, sessionManager);

// 启动 Unix socket 服务
const socketService = new UnixSocketService(claudeService);

socketService.start()
  .then(socketPath => {
    // socket 路径已经在 UnixSocketService 中输出
    logger.info('Server started successfully');
  })
  .catch(error => {
    logger.error('Failed to start server:', error);
    process.exit(1);
  });

// 优雅关闭
const shutdown = () => {
  logger.info('Shutting down server...');
  socketService.stop();
  process.exit(0);
};

process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);

// 处理未捕获的异常
process.on('uncaughtException', (error) => {
  logger.error('Uncaught exception:', error);
  shutdown();
});

process.on('unhandledRejection', (reason, promise) => {
  logger.error('Unhandled rejection at:', promise, 'reason:', reason);
  shutdown();
});

export { logger };