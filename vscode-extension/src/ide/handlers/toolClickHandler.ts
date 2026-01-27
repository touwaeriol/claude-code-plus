/**
 * Tool Click Handler Interface
 */

export interface ToolClickContext {
    toolName: string;
    toolUseId: string;
    input: Record<string, unknown>;
    result?: string;
    filePath?: string;
}

export interface ToolClickHandler {
    canHandle(toolName: string): boolean;
    handle(context: ToolClickContext): Promise<void>;
}
