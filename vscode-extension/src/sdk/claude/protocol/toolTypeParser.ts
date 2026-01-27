/**
 * Tool Type Parser for Claude CLI
 * 
 * This module parses generic ToolUseBlock into specific tool types.
 * Each tool type provides strongly-typed access to its parameters.
 */

import type { ToolUseBlock, ContentBlock } from './models';

/**
 * Tool type enumeration.
 * Defines all known Claude Code tool types.
 */
export enum ToolType {
  // Basic file operation tools
  BASH = 'CLAUDE_BASH',
  BASH_OUTPUT = 'CLAUDE_BASH_OUTPUT',
  KILL_SHELL = 'CLAUDE_KILL_SHELL',
  EDIT = 'CLAUDE_EDIT',
  MULTI_EDIT = 'CLAUDE_MULTI_EDIT',
  READ = 'CLAUDE_READ',
  WRITE = 'CLAUDE_WRITE',

  // Search and find tools
  GLOB = 'CLAUDE_GLOB',
  GREP = 'CLAUDE_GREP',

  // Network tools
  WEB_FETCH = 'CLAUDE_WEB_FETCH',
  WEB_SEARCH = 'CLAUDE_WEB_SEARCH',

  // Development and task management tools
  TODO_WRITE = 'CLAUDE_TODO_WRITE',
  TASK = 'CLAUDE_TASK',
  EXIT_PLAN_MODE = 'CLAUDE_EXIT_PLAN_MODE',
  ASK_USER_QUESTION = 'CLAUDE_ASK_USER_QUESTION',
  SKILL = 'CLAUDE_SKILL',
  SLASH_COMMAND = 'CLAUDE_SLASH_COMMAND',
  ENTER_PLAN_MODE = 'CLAUDE_ENTER_PLAN_MODE',

  // Jupyter notebook tool
  NOTEBOOK_EDIT = 'CLAUDE_NOTEBOOK_EDIT',

  // MCP (Model Context Protocol) tools
  MCP_TOOL = 'MCP',
  LIST_MCP_RESOURCES = 'CLAUDE_LIST_MCP_RESOURCES',
  READ_MCP_RESOURCE = 'CLAUDE_READ_MCP_RESOURCE',

  // Unknown tool type
  UNKNOWN = 'UNKNOWN',
}

/**
 * Get ToolType from tool name.
 */
export function getToolTypeFromName(toolName: string): ToolType {
  if (toolName.startsWith('mcp__')) {
    return ToolType.MCP_TOOL;
  }

  switch (toolName) {
    case 'Bash':
      return ToolType.BASH;
    case 'BashOutput':
      return ToolType.BASH_OUTPUT;
    case 'KillShell':
      return ToolType.KILL_SHELL;
    case 'Edit':
      return ToolType.EDIT;
    case 'MultiEdit':
      return ToolType.MULTI_EDIT;
    case 'Read':
      return ToolType.READ;
    case 'Write':
      return ToolType.WRITE;
    case 'Glob':
      return ToolType.GLOB;
    case 'Grep':
      return ToolType.GREP;
    case 'WebFetch':
      return ToolType.WEB_FETCH;
    case 'WebSearch':
      return ToolType.WEB_SEARCH;
    case 'TodoWrite':
      return ToolType.TODO_WRITE;
    case 'Task':
      return ToolType.TASK;
    case 'ExitPlanMode':
      return ToolType.EXIT_PLAN_MODE;
    case 'AskUserQuestion':
      return ToolType.ASK_USER_QUESTION;
    case 'Skill':
      return ToolType.SKILL;
    case 'NotebookEdit':
      return ToolType.NOTEBOOK_EDIT;
    case 'ListMcpResourcesTool':
      return ToolType.LIST_MCP_RESOURCES;
    case 'ReadMcpResourceTool':
      return ToolType.READ_MCP_RESOURCE;
    default:
      return ToolType.UNKNOWN;
  }
}

// ============================================================================
// Specific Tool Use Interfaces
// ============================================================================

/**
 * Base interface for specific tool uses.
 */
export interface SpecificToolUse extends ContentBlock {
  type: 'tool_use';
  id: string;
  name: string;
  input: unknown;
  toolType: ToolType;
}

/**
 * Bash tool use - execute shell commands.
 */
export interface BashToolUse extends SpecificToolUse {
  command: string;
  description?: string;
  timeout?: number;
  runInBackground?: boolean;
}

/**
 * BashOutput tool use - get output from a Bash command.
 */
export interface BashOutputToolUse extends SpecificToolUse {
  bashId: string;
  filter?: string;
}

/**
 * KillShell tool use - terminate a running shell process.
 */
export interface KillShellToolUse extends SpecificToolUse {
  shellId: string;
}

/**
 * Edit tool use - edit file content.
 */
export interface EditToolUse extends SpecificToolUse {
  filePath: string;
  oldString: string;
  newString: string;
  replaceAll?: boolean;
}

/**
 * Edit operation for MultiEdit.
 */
export interface EditOperation {
  oldString: string;
  newString: string;
  replaceAll?: boolean;
}

/**
 * MultiEdit tool use - multiple edits to a single file.
 */
export interface MultiEditToolUse extends SpecificToolUse {
  filePath: string;
  edits: EditOperation[];
}

/**
 * Read tool use - read file content.
 */
export interface ReadToolUse extends SpecificToolUse {
  filePath: string;
  offset?: number;
  limit?: number;
}

/**
 * Write tool use - write file content.
 */
export interface WriteToolUse extends SpecificToolUse {
  filePath: string;
  content: string;
}

/**
 * Glob tool use - file pattern matching.
 */
export interface GlobToolUse extends SpecificToolUse {
  pattern: string;
  path?: string;
}

/**
 * Grep tool use - text search.
 */
export interface GrepToolUse extends SpecificToolUse {
  pattern: string;
  path?: string;
  outputMode?: string;
  glob?: string;
  grepType?: string;
  caseInsensitive?: boolean;
  showLineNumbers?: boolean;
  contextBefore?: number;
  contextAfter?: number;
  headLimit?: number;
}

/**
 * WebFetch tool use - fetch web content.
 */
export interface WebFetchToolUse extends SpecificToolUse {
  url: string;
  prompt: string;
}

/**
 * WebSearch tool use - web search.
 */
export interface WebSearchToolUse extends SpecificToolUse {
  query: string;
  allowedDomains?: string[];
  blockedDomains?: string[];
}

/**
 * Todo item for TodoWrite.
 */
export interface TodoItem {
  content: string;
  status: string; // 'pending' | 'in_progress' | 'completed'
  activeForm: string;
}

/**
 * TodoWrite tool use - manage todo list.
 */
export interface TodoWriteToolUse extends SpecificToolUse {
  todos: TodoItem[];
}

/**
 * Task tool use - launch sub-task agent.
 */
export interface TaskToolUse extends SpecificToolUse {
  description: string;
  prompt: string;
  subagentType: string;
}

/**
 * NotebookEdit tool use - edit Jupyter Notebook.
 */
export interface NotebookEditToolUse extends SpecificToolUse {
  notebookPath: string;
  newSource: string;
  cellId?: string;
  cellType?: string;
  editMode?: string;
}

/**
 * ExitPlanMode tool use - exit plan mode.
 */
export interface ExitPlanModeToolUse extends SpecificToolUse {
  plan: string;
}

/**
 * MCP tool use - Model Context Protocol tool.
 */
export interface McpToolUse extends SpecificToolUse {
  fullToolName: string;
  serverName: string;
  functionName: string;
  parameters: Record<string, unknown>;
}

/**
 * ListMcpResources tool use - list MCP server resources.
 */
export interface ListMcpResourcesToolUse extends SpecificToolUse {
  server?: string;
}

/**
 * ReadMcpResource tool use - read MCP resource.
 */
export interface ReadMcpResourceToolUse extends SpecificToolUse {
  server: string;
  uri: string;
}

/**
 * Skill tool use - invoke registered skills.
 */
export interface SkillToolUse extends SpecificToolUse {
  skill: string;
  args?: string;
}

/**
 * Unknown tool use - for unrecognized tool types.
 */
export interface UnknownToolUse extends SpecificToolUse {
  toolName: string;
  parameters: Record<string, unknown>;
}

// ============================================================================
// Tool Type Parser
// ============================================================================

/**
 * Tool Type Parser - converts generic ToolUseBlock to specific tool types.
 */
export const ToolTypeParser = {
  /**
   * Parse ToolUseBlock into a specific tool type.
   */
  parseToolUseBlock(block: ToolUseBlock): SpecificToolUse {
    try {
      switch (block.name) {
        case 'Bash':
          return this.parseBashTool(block);
        case 'BashOutput':
          return this.parseBashOutputTool(block);
        case 'KillShell':
          return this.parseKillShellTool(block);
        case 'Edit':
          return this.parseEditTool(block);
        case 'MultiEdit':
          return this.parseMultiEditTool(block);
        case 'Read':
          return this.parseReadTool(block);
        case 'Write':
          return this.parseWriteTool(block);
        case 'Glob':
          return this.parseGlobTool(block);
        case 'Grep':
          return this.parseGrepTool(block);
        case 'WebFetch':
          return this.parseWebFetchTool(block);
        case 'WebSearch':
          return this.parseWebSearchTool(block);
        case 'TodoWrite':
          return this.parseTodoWriteTool(block);
        case 'Task':
          return this.parseTaskTool(block);
        case 'NotebookEdit':
          return this.parseNotebookEditTool(block);
        case 'ExitPlanMode':
          return this.parseExitPlanModeTool(block);
        case 'ListMcpResourcesTool':
          return this.parseListMcpResourcesTool(block);
        case 'ReadMcpResourceTool':
          return this.parseReadMcpResourceTool(block);
        case 'Skill':
          return this.parseSkillTool(block);
        default:
          if (block.name.startsWith('mcp__')) {
            return this.parseMcpTool(block);
          }
          return this.parseUnknownTool(block);
      }
    } catch (e) {
      console.warn(`[ToolTypeParser] Failed to parse tool ${block.name}:`, e);
      return this.parseUnknownTool(block);
    }
  },

  /**
   * Get input as object.
   */
  getInputObject(input: unknown): Record<string, unknown> {
    if (typeof input === 'object' && input !== null) {
      return input as Record<string, unknown>;
    }
    return {};
  },

  parseBashTool(block: ToolUseBlock): BashToolUse {
    const params = this.getInputObject(block.input);
    return {
      type: 'tool_use',
      id: block.id,
      name: block.name,
      input: block.input,
      toolType: ToolType.BASH,
      command: (params.command as string) || '',
      description: params.description as string | undefined,
      timeout: params.timeout as number | undefined,
      runInBackground: (params.run_in_background as boolean) ?? false,
    };
  },

  parseBashOutputTool(block: ToolUseBlock): BashOutputToolUse {
    const params = this.getInputObject(block.input);
    return {
      type: 'tool_use',
      id: block.id,
      name: block.name,
      input: block.input,
      toolType: ToolType.BASH_OUTPUT,
      bashId: (params.bash_id as string) || '',
      filter: params.filter as string | undefined,
    };
  },

  parseKillShellTool(block: ToolUseBlock): KillShellToolUse {
    const params = this.getInputObject(block.input);
    return {
      type: 'tool_use',
      id: block.id,
      name: block.name,
      input: block.input,
      toolType: ToolType.KILL_SHELL,
      shellId: (params.shell_id as string) || '',
    };
  },

  parseEditTool(block: ToolUseBlock): EditToolUse {
    const params = this.getInputObject(block.input);
    return {
      type: 'tool_use',
      id: block.id,
      name: block.name,
      input: block.input,
      toolType: ToolType.EDIT,
      filePath: (params.file_path as string) || '',
      oldString: (params.old_string as string) || '',
      newString: (params.new_string as string) || '',
      replaceAll: (params.replace_all as boolean) ?? false,
    };
  },

  parseMultiEditTool(block: ToolUseBlock): MultiEditToolUse {
    const params = this.getInputObject(block.input);
    const editsArray = params.edits as unknown[] | undefined;

    const edits: EditOperation[] = (editsArray ?? []).map((edit) => {
      const editObj = edit as Record<string, unknown>;
      return {
        oldString: (editObj.old_string as string) || '',
        newString: (editObj.new_string as string) || '',
        replaceAll: (editObj.replace_all as boolean) ?? false,
      };
    });

    return {
      type: 'tool_use',
      id: block.id,
      name: block.name,
      input: block.input,
      toolType: ToolType.MULTI_EDIT,
      filePath: (params.file_path as string) || '',
      edits,
    };
  },

  parseReadTool(block: ToolUseBlock): ReadToolUse {
    const params = this.getInputObject(block.input);
    return {
      type: 'tool_use',
      id: block.id,
      name: block.name,
      input: block.input,
      toolType: ToolType.READ,
      filePath: (params.file_path as string) || '',
      offset: params.offset as number | undefined,
      limit: params.limit as number | undefined,
    };
  },

  parseWriteTool(block: ToolUseBlock): WriteToolUse {
    const params = this.getInputObject(block.input);
    return {
      type: 'tool_use',
      id: block.id,
      name: block.name,
      input: block.input,
      toolType: ToolType.WRITE,
      filePath: (params.file_path as string) || '',
      content: (params.content as string) || '',
    };
  },

  parseGlobTool(block: ToolUseBlock): GlobToolUse {
    const params = this.getInputObject(block.input);
    return {
      type: 'tool_use',
      id: block.id,
      name: block.name,
      input: block.input,
      toolType: ToolType.GLOB,
      pattern: (params.pattern as string) || '',
      path: params.path as string | undefined,
    };
  },

  parseGrepTool(block: ToolUseBlock): GrepToolUse {
    const params = this.getInputObject(block.input);
    return {
      type: 'tool_use',
      id: block.id,
      name: block.name,
      input: block.input,
      toolType: ToolType.GREP,
      pattern: (params.pattern as string) || '',
      path: params.path as string | undefined,
      outputMode: params.output_mode as string | undefined,
      glob: params.glob as string | undefined,
      grepType: params.type as string | undefined,
      caseInsensitive: (params['-i'] as boolean) ?? false,
      showLineNumbers: (params['-n'] as boolean) ?? false,
      contextBefore: params['-B'] as number | undefined,
      contextAfter: params['-A'] as number | undefined,
      headLimit: params.head_limit as number | undefined,
    };
  },

  parseWebFetchTool(block: ToolUseBlock): WebFetchToolUse {
    const params = this.getInputObject(block.input);
    return {
      type: 'tool_use',
      id: block.id,
      name: block.name,
      input: block.input,
      toolType: ToolType.WEB_FETCH,
      url: (params.url as string) || '',
      prompt: (params.prompt as string) || '',
    };
  },

  parseWebSearchTool(block: ToolUseBlock): WebSearchToolUse {
    const params = this.getInputObject(block.input);
    return {
      type: 'tool_use',
      id: block.id,
      name: block.name,
      input: block.input,
      toolType: ToolType.WEB_SEARCH,
      query: (params.query as string) || '',
      allowedDomains: params.allowed_domains as string[] | undefined,
      blockedDomains: params.blocked_domains as string[] | undefined,
    };
  },

  parseTodoWriteTool(block: ToolUseBlock): TodoWriteToolUse {
    const params = this.getInputObject(block.input);
    const todosArray = params.todos as unknown[] | undefined;

    const todos: TodoItem[] = (todosArray ?? []).map((todo) => {
      const todoObj = todo as Record<string, unknown>;
      return {
        content: (todoObj.content as string) || '',
        status: (todoObj.status as string) || 'pending',
        activeForm: (todoObj.activeForm as string) || '',
      };
    });

    return {
      type: 'tool_use',
      id: block.id,
      name: block.name,
      input: block.input,
      toolType: ToolType.TODO_WRITE,
      todos,
    };
  },

  parseTaskTool(block: ToolUseBlock): TaskToolUse {
    const params = this.getInputObject(block.input);
    return {
      type: 'tool_use',
      id: block.id,
      name: block.name,
      input: block.input,
      toolType: ToolType.TASK,
      description: (params.description as string) || '',
      prompt: (params.prompt as string) || '',
      subagentType: (params.subagent_type as string) || '',
    };
  },

  parseNotebookEditTool(block: ToolUseBlock): NotebookEditToolUse {
    const params = this.getInputObject(block.input);
    return {
      type: 'tool_use',
      id: block.id,
      name: block.name,
      input: block.input,
      toolType: ToolType.NOTEBOOK_EDIT,
      notebookPath: (params.notebook_path as string) || '',
      newSource: (params.new_source as string) || '',
      cellId: params.cell_id as string | undefined,
      cellType: params.cell_type as string | undefined,
      editMode: params.edit_mode as string | undefined,
    };
  },

  parseExitPlanModeTool(block: ToolUseBlock): ExitPlanModeToolUse {
    const params = this.getInputObject(block.input);
    return {
      type: 'tool_use',
      id: block.id,
      name: block.name,
      input: block.input,
      toolType: ToolType.EXIT_PLAN_MODE,
      plan: (params.plan as string) || '',
    };
  },

  parseListMcpResourcesTool(block: ToolUseBlock): ListMcpResourcesToolUse {
    const params = this.getInputObject(block.input);
    return {
      type: 'tool_use',
      id: block.id,
      name: block.name,
      input: block.input,
      toolType: ToolType.LIST_MCP_RESOURCES,
      server: params.server as string | undefined,
    };
  },

  parseReadMcpResourceTool(block: ToolUseBlock): ReadMcpResourceToolUse {
    const params = this.getInputObject(block.input);
    return {
      type: 'tool_use',
      id: block.id,
      name: block.name,
      input: block.input,
      toolType: ToolType.READ_MCP_RESOURCE,
      server: (params.server as string) || '',
      uri: (params.uri as string) || '',
    };
  },

  parseSkillTool(block: ToolUseBlock): SkillToolUse {
    const params = this.getInputObject(block.input);
    return {
      type: 'tool_use',
      id: block.id,
      name: block.name,
      input: block.input,
      toolType: ToolType.SKILL,
      skill: (params.skill as string) || '',
      args: params.args as string | undefined,
    };
  },

  parseMcpTool(block: ToolUseBlock): McpToolUse {
    // MCP tool name format: mcp__server_name__function_name
    const nameParts = block.name.split('__');
    const serverName = nameParts.length >= 2 ? nameParts[1] : 'unknown';
    const functionName = nameParts.length >= 3 ? nameParts[2] : block.name;

    const parameters = this.getInputObject(block.input);

    return {
      type: 'tool_use',
      id: block.id,
      name: block.name,
      input: block.input,
      toolType: ToolType.MCP_TOOL,
      fullToolName: block.name,
      serverName,
      functionName,
      parameters,
    };
  },

  parseUnknownTool(block: ToolUseBlock): UnknownToolUse {
    const parameters = this.getInputObject(block.input);
    return {
      type: 'tool_use',
      id: block.id,
      name: block.name,
      input: block.input,
      toolType: ToolType.UNKNOWN,
      toolName: block.name,
      parameters,
    };
  },
};
