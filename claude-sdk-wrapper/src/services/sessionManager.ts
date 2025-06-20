import { v4 as uuidv4 } from 'uuid';
import winston from 'winston';

export interface Session {
  id: string;
  created_at: Date;
  last_activity: Date;
  message_count: number;
  is_first_message: boolean;
}

/**
 * 单会话管理器 - 每个 Node 服务实例只支持一个会话
 */
export class SessionManager {
  private currentSession: Session | null = null;
  private sessionTimeout = 2 * 60 * 60 * 1000; // 2 hours

  constructor(private logger: winston.Logger) {
    // 定期检查会话是否过期
    setInterval(() => {
      this.checkSessionExpiry();
    }, 5 * 60 * 1000); // 每5分钟检查一次
  }

  /**
   * 创建新会话（会替换当前会话）
   */
  createSession(): string {
    const sessionId = uuidv4();
    const session: Session = {
      id: sessionId,
      created_at: new Date(),
      last_activity: new Date(),
      message_count: 0,
      is_first_message: true
    };
    
    // 如果已有会话，先记录
    if (this.currentSession) {
      this.logger.info(`Replacing existing session ${this.currentSession.id} with new session ${sessionId}`);
    }
    
    this.currentSession = session;
    this.logger.info(`Created new session: ${sessionId}`);
    return sessionId;
  }

  /**
   * 获取当前会话
   */
  getSession(sessionId: string): Session | null {
    // 检查是否是当前会话
    if (!this.currentSession || this.currentSession.id !== sessionId) {
      this.logger.warn(`Session ${sessionId} not found or not current`);
      return null;
    }

    // 检查会话是否过期
    if (Date.now() - this.currentSession.last_activity.getTime() > this.sessionTimeout) {
      this.logger.info(`Session ${sessionId} expired`);
      this.currentSession = null;
      return null;
    }

    return this.currentSession;
  }

  /**
   * 更新当前会话
   */
  updateSession(sessionId: string, updates: Partial<Session>): void {
    if (this.currentSession && this.currentSession.id === sessionId) {
      Object.assign(this.currentSession, updates, { last_activity: new Date() });
    }
  }

  /**
   * 获取或创建会话
   */
  getOrCreateSession(): string {
    // 如果有当前会话且未过期，返回它
    if (this.currentSession) {
      const isExpired = Date.now() - this.currentSession.last_activity.getTime() > this.sessionTimeout;
      if (!isExpired) {
        return this.currentSession.id;
      }
    }

    // 否则创建新会话
    return this.createSession();
  }

  /**
   * 检查会话是否过期
   */
  private checkSessionExpiry(): void {
    if (this.currentSession) {
      const isExpired = Date.now() - this.currentSession.last_activity.getTime() > this.sessionTimeout;
      if (isExpired) {
        this.logger.info(`Session ${this.currentSession.id} expired, clearing`);
        this.currentSession = null;
      }
    }
  }

  /**
   * 获取当前会话（如果存在）
   */
  getCurrentSession(): Session | null {
    this.checkSessionExpiry();
    return this.currentSession;
  }

  /**
   * 清除当前会话
   */
  clearSession(): void {
    if (this.currentSession) {
      this.logger.info(`Clearing session ${this.currentSession.id}`);
      this.currentSession = null;
    }
  }
}