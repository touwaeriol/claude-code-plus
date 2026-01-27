/**
 * Tool Constants
 * 
 * Tool type constants matching frontend/src/constants/toolTypes.ts
 * Translated from jetbrains-plugin/src/main/kotlin/com/asakii/plugin/types/ToolConstants.kt
 */

/**
 * Tool type constants
 */
export const ToolConstants = {
    // File operation tools
    READ: 'Read',
    WRITE: 'Write',
    EDIT: 'Edit',
    MULTI_EDIT: 'MultiEdit',
    NOTEBOOK_EDIT: 'NotebookEdit',
    
    // Command execution tools
    BASH: 'Bash',
    BASH_OUTPUT: 'BashOutput',
    TASK: 'Task',
    KILL_SHELL: 'KillShell',
    
    // Search tools
    GREP: 'Grep',
    GLOB: 'Glob',
    
    // Web tools
    WEB_SEARCH: 'WebSearch',
    WEB_FETCH: 'WebFetch',
    
    // Task and plan tools
    TODO_WRITE: 'TodoWrite',
    EXIT_PLAN_MODE: 'ExitPlanMode',
    
    // User interaction tools
    ASK_USER_QUESTION: 'AskUserQuestion',
    
    // Skill and command tools
    SKILL: 'Skill',
    SLASH_COMMAND: 'SlashCommand',
    
    // MCP tools
    LIST_MCP_RESOURCES: 'ListMcpResourcesTool',
    READ_MCP_RESOURCE: 'ReadMcpResourceTool'
} as const;

export type ToolType = typeof ToolConstants[keyof typeof ToolConstants];

/**
 * Tool name to type mapping
 */
export const TOOL_NAME_TO_TYPE: Record<string, string> = {
    'Read': ToolConstants.READ,
    'Write': ToolConstants.WRITE,
    'Edit': ToolConstants.EDIT,
    'MultiEdit': ToolConstants.MULTI_EDIT,
    'NotebookEdit': ToolConstants.NOTEBOOK_EDIT,
    'Bash': ToolConstants.BASH,
    'BashOutput': ToolConstants.BASH_OUTPUT,
    'Task': ToolConstants.TASK,
    'KillShell': ToolConstants.KILL_SHELL,
    'Grep': ToolConstants.GREP,
    'Glob': ToolConstants.GLOB,
    'WebSearch': ToolConstants.WEB_SEARCH,
    'WebFetch': ToolConstants.WEB_FETCH,
    'TodoWrite': ToolConstants.TODO_WRITE,
    'ExitPlanMode': ToolConstants.EXIT_PLAN_MODE,
    'AskUserQuestion': ToolConstants.ASK_USER_QUESTION,
    'Skill': ToolConstants.SKILL,
    'SlashCommand': ToolConstants.SLASH_COMMAND,
    'ListMcpResourcesTool': ToolConstants.LIST_MCP_RESOURCES,
    'ReadMcpResourceTool': ToolConstants.READ_MCP_RESOURCE
};

/**
 * Check if a tool is a file operation tool
 */
export function isFileOperationTool(toolType: string): boolean {
    return [
        ToolConstants.READ,
        ToolConstants.WRITE,
        ToolConstants.EDIT,
        ToolConstants.MULTI_EDIT,
        ToolConstants.NOTEBOOK_EDIT
    ].includes(toolType as any);
}

/**
 * Check if a tool is a command execution tool
 */
export function isCommandExecutionTool(toolType: string): boolean {
    return [
        ToolConstants.BASH,
        ToolConstants.BASH_OUTPUT,
        ToolConstants.TASK,
        ToolConstants.KILL_SHELL
    ].includes(toolType as any);
}

/**
 * Check if a tool is a search tool
 */
export function isSearchTool(toolType: string): boolean {
    return [
        ToolConstants.GREP,
        ToolConstants.GLOB
    ].includes(toolType as any);
}

/**
 * Check if a tool is a web tool
 */
export function isWebTool(toolType: string): boolean {
    return [
        ToolConstants.WEB_SEARCH,
        ToolConstants.WEB_FETCH
    ].includes(toolType as any);
}
