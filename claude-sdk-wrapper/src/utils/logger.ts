import winston from 'winston';

/**
 * 日志记录器包装类
 */
export class Logger {
  private logger: winston.Logger;
  
  constructor(name: string) {
    this.logger = winston.createLogger({
      level: process.env.LOG_LEVEL || 'info',
      format: winston.format.combine(
        winston.format.timestamp(),
        winston.format.printf(({ timestamp, level, message }) => {
          return `${timestamp} [${name}] [${level.toUpperCase()}] ${message}`;
        })
      ),
      transports: [
        new winston.transports.Console()
      ]
    });
  }
  
  info(message: string, ...args: any[]) {
    this.logger.info(message, ...args);
  }
  
  warn(message: string, ...args: any[]) {
    this.logger.warn(message, ...args);
  }
  
  error(message: string, ...args: any[]) {
    this.logger.error(message, ...args);
  }
  
  debug(message: string, ...args: any[]) {
    this.logger.debug(message, ...args);
  }
}