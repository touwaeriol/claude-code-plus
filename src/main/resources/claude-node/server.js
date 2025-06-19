"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.logger = void 0;
const winston_1 = __importDefault(require("winston"));
const claudeService_1 = require("./services/claudeService");
const sessionManager_1 = require("./services/sessionManager");
const unixSocketService_1 = require("./services/unixSocketService");
const logger = winston_1.default.createLogger({
    level: 'error',
    format: winston_1.default.format.combine(winston_1.default.format.timestamp(), winston_1.default.format.printf(({ timestamp, level, message }) => {
        return `${timestamp} [${level.toUpperCase()}] ${message}`;
    })),
    transports: [
        new winston_1.default.transports.Console()
    ]
});
exports.logger = logger;
const sessionManager = new sessionManager_1.SessionManager(logger);
const claudeService = new claudeService_1.ClaudeService(logger, sessionManager);
const socketService = new unixSocketService_1.UnixSocketService(claudeService);
socketService.start()
    .then(socketPath => {
    logger.info('Server started successfully');
})
    .catch(error => {
    logger.error('Failed to start server:', error);
    process.exit(1);
});
const shutdown = () => {
    logger.info('Shutting down server...');
    socketService.stop();
    process.exit(0);
};
process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);
process.on('uncaughtException', (error) => {
    logger.error('Uncaught exception:', error);
    shutdown();
});
process.on('unhandledRejection', (reason, promise) => {
    logger.error('Unhandled rejection at:', promise, 'reason:', reason);
    shutdown();
});
