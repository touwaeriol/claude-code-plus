/**
 * Tool Click Manager
 * Manages tool click handlers and dispatches click events
 */

import { ToolClickHandler, ToolClickContext } from './toolClickHandler';
import { ReadToolHandler } from './readToolHandler';
import { WriteToolHandler } from './writeToolHandler';
import { EditToolHandler } from './editToolHandler';

export class ToolClickManager {
    private static instance: ToolClickManager | null = null;
    private handlers: ToolClickHandler[] = [];

    private constructor() {
        // Register default handlers
        this.registerHandler(new ReadToolHandler());
        this.registerHandler(new WriteToolHandler());
        this.registerHandler(new EditToolHandler());
    }

    static getInstance(): ToolClickManager {
        if (!ToolClickManager.instance) {
            ToolClickManager.instance = new ToolClickManager();
        }
        return ToolClickManager.instance;
    }

    registerHandler(handler: ToolClickHandler): void {
        this.handlers.push(handler);
    }

    async handleClick(context: ToolClickContext): Promise<boolean> {
        for (const handler of this.handlers) {
            if (handler.canHandle(context.toolName)) {
                await handler.handle(context);
                return true;
            }
        }
        return false;
    }

    dispose(): void {
        this.handlers = [];
        ToolClickManager.instance = null;
    }
}

export const toolClickManager = ToolClickManager.getInstance();
