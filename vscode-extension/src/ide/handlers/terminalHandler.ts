/**
 * 终端后台执行处理器
 * 处理: getBackgroundableTerminals, terminalBackground
 * 
 * 翻译自: jetbrains-plugin/.../handlers/TerminalHandler.kt
 */

import {
  JetBrainsTerminalBackgroundRequest,
  JetBrainsTerminalBackgroundEvent,
  JetBrainsGetBackgroundableTerminalsResponse,
  JetBrainsBackgroundableTerminal,
  TerminalBackgroundStatus,
} from '@proto';
import { TerminalTaskManager } from '../terminal/terminalTaskManager';

// 日志工具
const log = {
  info: (msg: string) => console.log(`[TerminalHandler] ${msg}`),
  warn: (msg: string) => console.warn(`[TerminalHandler] ${msg}`),
  error: (msg: string) => console.error(`[TerminalHandler] ${msg}`),
};

export class TerminalHandler {
  constructor(private terminalTaskManager?: TerminalTaskManager) {}

  /**
   * 获取可后台化的终端任务
   */
  handleGetBackgroundableTerminals(): JetBrainsGetBackgroundableTerminalsResponse {
    try {
      if (!this.terminalTaskManager) {
        log.warn('⚠️ Terminal Task Manager not available');
        return JetBrainsGetBackgroundableTerminalsResponse.create({
          success: false,
          error: 'Terminal Task Manager not available',
          terminals: [],
        });
      }

      const tasks = this.terminalTaskManager.getBackgroundableTasks();
      log.info(`📋 getBackgroundableTerminals: returning ${tasks.length} tasks to frontend`);

      const terminals: JetBrainsBackgroundableTerminal[] = tasks.map(task => 
        JetBrainsBackgroundableTerminal.create({
          sessionId: task.sessionId,
          toolUseId: task.toolUseId,
          command: task.command,
          startTime: BigInt(task.startTime),
          elapsedMs: BigInt(task.getElapsedMs()),
        })
      );

      return JetBrainsGetBackgroundableTerminalsResponse.create({
        success: true,
        terminals,
      });
    } catch (e) {
      const error = e instanceof Error ? e.message : 'Unknown error';
      log.error(`❌ getBackgroundableTerminals failed: ${error}`);
      return JetBrainsGetBackgroundableTerminalsResponse.create({
        success: false,
        error,
        terminals: [],
      });
    }
  }

  /**
   * 批量后台终端任务（生成器，流式返回结果）
   */
  async *handleTerminalBackground(
    request: JetBrainsTerminalBackgroundRequest
  ): AsyncGenerator<JetBrainsTerminalBackgroundEvent> {
    log.info(`⏸️ terminalBackground: ${request.items.length} items`);

    if (!this.terminalTaskManager) {
      log.warn('⚠️ Terminal Task Manager not available');
      yield this.buildTerminalBackgroundEvent(
        '',
        '',
        TerminalBackgroundStatus.TERMINAL_BG_FAILED,
        'Terminal Task Manager not available'
      );
      return;
    }

    for (const item of request.items) {
      const sessionId = item.sessionId;
      const toolUseId = item.toolUseId;

      // 发送开始事件
      yield this.buildTerminalBackgroundEvent(
        sessionId,
        toolUseId,
        TerminalBackgroundStatus.TERMINAL_BG_STARTED,
        undefined
      );

      try {
        const success = this.terminalTaskManager.markTaskAsBackground(toolUseId);

        if (success) {
          log.info(`✅ terminal background success: ${toolUseId}`);
          yield this.buildTerminalBackgroundEvent(
            sessionId,
            toolUseId,
            TerminalBackgroundStatus.TERMINAL_BG_SUCCESS,
            undefined
          );
        } else {
          log.warn(`❌ terminal background failed: ${toolUseId} - Task not found`);
          yield this.buildTerminalBackgroundEvent(
            sessionId,
            toolUseId,
            TerminalBackgroundStatus.TERMINAL_BG_FAILED,
            'Task not found'
          );
        }
      } catch (e) {
        const error = e instanceof Error ? e.message : 'Unknown error';
        log.error(`❌ terminal background exception: ${toolUseId} - ${error}`);
        yield this.buildTerminalBackgroundEvent(
          sessionId,
          toolUseId,
          TerminalBackgroundStatus.TERMINAL_BG_FAILED,
          error
        );
      }
    }
  }

  /**
   * 构建终端后台事件
   */
  private buildTerminalBackgroundEvent(
    sessionId: string,
    toolUseId: string,
    status: TerminalBackgroundStatus,
    error: string | undefined
  ): JetBrainsTerminalBackgroundEvent {
    return JetBrainsTerminalBackgroundEvent.create({
      sessionId,
      toolUseId,
      status,
      ...(error !== undefined && { error }),
    });
  }
}
