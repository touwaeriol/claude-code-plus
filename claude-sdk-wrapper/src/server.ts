import { WebSocketService } from './services/webSocketService';
import { Logger } from './utils/logger';

const logger = new Logger('Server');

async function main() {
    try {
        // 从命令行参数获取端口，默认使用 9925
        const port = parseInt(process.argv[2] || '9925', 10);
        
        logger.info('Starting Claude SDK server...');
        logger.info(`Port: ${port}`);
        
        // 输出端口到标准输出，供插件读取
        console.log(`PORT: ${port}`);
        
        const service = new WebSocketService(port);
        await service.start();
        
        // 处理进程信号
        process.on('SIGINT', () => {
            logger.info('Received SIGINT, shutting down...');
            service.stop();
            process.exit(0);
        });
        
        process.on('SIGTERM', () => {
            logger.info('Received SIGTERM, shutting down...');
            service.stop();
            process.exit(0);
        });
        
        // 未捕获的异常处理
        process.on('uncaughtException', (error) => {
            logger.error('Uncaught exception:', error);
            console.error('Uncaught exception:', error);
            process.exit(1);
        });
        
        process.on('unhandledRejection', (reason, promise) => {
            logger.error('Unhandled rejection at:', promise, 'reason:', reason);
            console.error('Unhandled rejection:', reason);
            process.exit(1);
        });
        
    } catch (error) {
        logger.error('Failed to start server:', error);
        console.error('Failed to start server:', error);
        process.exit(1);
    }
}

main();