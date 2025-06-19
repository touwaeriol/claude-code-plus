import { v4 as uuidv4 } from 'uuid';
import { Logger } from 'winston';

export interface Session {
  id: string;
  created_at: Date;
  last_activity: Date;
  message_count: number;
  is_first_message: boolean;
}

export class SessionManager {
  private sessions: Map<string, Session> = new Map();
  private sessionTimeout = 2 * 60 * 60 * 1000; // 2 hours
  private defaultSessionId: string | null = null;

  constructor(private logger: Logger) {
    // 定期清理过期会话
    setInterval(() => {
      this.cleanExpiredSessions();
    }, 5 * 60 * 1000); // 每5分钟清理一次
  }

  /**
   * 创建新会话
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
    
    this.sessions.set(sessionId, session);
    this.logger.info(`Created new session: ${sessionId}`);
    return sessionId;
  }

  /**
   * 获取会话
   */
  getSession(sessionId: string): Session | null {
    const session = this.sessions.get(sessionId);
    if (!session) {
      return null;
    }

    // 检查会话是否过期
    if (Date.now() - session.last_activity.getTime() > this.sessionTimeout) {
      this.logger.info(`Session ${sessionId} expired`);
      this.sessions.delete(sessionId);
      if (this.defaultSessionId === sessionId) {
        this.defaultSessionId = null;
      }
      return null;
    }

    return session;
  }

  /**
   * 更新会话
   */
  updateSession(sessionId: string, updates: Partial<Session>): void {
    const session = this.sessions.get(sessionId);
    if (session) {
      Object.assign(session, updates, { last_activity: new Date() });
    }
  }

  /**
   * 获取或创建默认会话
   */
  getOrCreateDefaultSession(): string {
    if (this.defaultSessionId) {
      const session = this.getSession(this.defaultSessionId);
      if (session) {
        return this.defaultSessionId;
      }
    }

    this.defaultSessionId = this.createSession();
    this.logger.info(`Created new default session: ${this.defaultSessionId}`);
    return this.defaultSessionId;
  }

  /**
   * 清理过期会话
   */
  cleanExpiredSessions(): void {
    const now = Date.now();
    const expired: string[] = [];

    for (const [id, session] of this.sessions) {
      if (now - session.last_activity.getTime() > this.sessionTimeout) {
        expired.push(id);
      }
    }

    for (const id of expired) {
      this.logger.info(`Cleaning expired session: ${id}`);
      this.sessions.delete(id);
      if (this.defaultSessionId === id) {
        this.defaultSessionId = null;
      }
    }
  }

  /**
   * 获取活跃会话列表
   */
  getActiveSessions(): Session[] {
    this.cleanExpiredSessions();
    return Array.from(this.sessions.values());
  }
}