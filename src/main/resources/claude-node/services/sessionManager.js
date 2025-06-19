"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.SessionManager = void 0;
const uuid_1 = require("uuid");
class SessionManager {
    constructor(logger) {
        this.logger = logger;
        this.sessions = new Map();
        this.sessionTimeout = 2 * 60 * 60 * 1000;
        this.defaultSessionId = null;
        setInterval(() => {
            this.cleanExpiredSessions();
        }, 5 * 60 * 1000);
    }
    createSession() {
        const sessionId = (0, uuid_1.v4)();
        const session = {
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
    getSession(sessionId) {
        const session = this.sessions.get(sessionId);
        if (!session) {
            return null;
        }
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
    updateSession(sessionId, updates) {
        const session = this.sessions.get(sessionId);
        if (session) {
            Object.assign(session, updates, { last_activity: new Date() });
        }
    }
    getOrCreateDefaultSession() {
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
    cleanExpiredSessions() {
        const now = Date.now();
        const expired = [];
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
    getActiveSessions() {
        this.cleanExpiredSessions();
        return Array.from(this.sessions.values());
    }
}
exports.SessionManager = SessionManager;
