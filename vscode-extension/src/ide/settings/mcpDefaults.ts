/**
 * MCP Defaults
 * 
 * Default configurations, tool schemas, and instructions for built-in MCP servers.
 * Translated from jetbrains-plugin/src/main/kotlin/com/asakii/settings/McpDefaults.kt
 */

import { AgentConfig } from './agentSettingsModels';

/**
 * MCP Default Configurations
 */
export const McpDefaults = {
    /**
     * Context7 MCP Server Configuration
     */
    Context7Server: {
        URL: 'https://mcp.context7.com/mcp',
        API_KEY_HEADER: 'CONTEXT7_API_KEY',
        DESCRIPTION: 'Context7 MCP - Fetch up-to-date documentation for libraries'
    },

    /**
     * JetBrains MCP Tools Schema (JSON format)
     */
    JETBRAINS_TOOLS_SCHEMA: `{
  "FileIndex": {
    "type": "object",
    "description": "Search files, classes, and symbols in the IDE index by keywords. Faster than file system search, supports fuzzy matching.",
    "properties": {
      "query": { "type": "string", "description": "Search keywords" },
      "searchType": { "type": "string", "enum": ["All", "Classes", "Files", "Symbols", "Actions", "Text"], "description": "Search type", "default": "All" },
      "scope": { "type": "string", "enum": ["Project", "All", "ProductionFiles", "TestFiles", "Scratches"], "description": "Search scope", "default": "Project" },
      "maxResults": { "type": "integer", "description": "Max results", "default": 20, "minimum": 1 },
      "offset": { "type": "integer", "description": "Offset", "default": 0, "minimum": 0 }
    },
    "required": ["query"]
  },
  "DirectoryTree": {
    "type": "object",
    "description": "Get the tree structure of the project directory. Supports depth limit, file filtering, and hidden files options.",
    "properties": {
      "path": { "type": "string", "description": "Path relative to project root", "default": "." },
      "maxDepth": { "type": "integer", "description": "Maximum recursion depth. Use -1 or 0 for unlimited depth.", "default": 3 },
      "filesOnly": { "type": "boolean", "description": "Show only files, hide directory entries", "default": false },
      "includeHidden": { "type": "boolean", "description": "Include hidden files/directories", "default": false },
      "pattern": { "type": "string", "description": "File name filter using glob patterns" },
      "maxEntries": { "type": "integer", "description": "Maximum number of entries to return", "default": 100, "minimum": 1 }
    },
    "required": []
  },
  "CodeSearch": {
    "type": "object",
    "description": "Search code content across project files (like IDE's Find in Files).",
    "properties": {
      "query": { "type": "string", "description": "Search text or regular expression pattern" },
      "isRegex": { "type": "boolean", "description": "Treat query as regular expression", "default": false },
      "caseSensitive": { "type": "boolean", "description": "Case sensitive search", "default": false },
      "wholeWords": { "type": "boolean", "description": "Match whole words only", "default": false },
      "fileMask": { "type": "string", "description": "File name filter using glob patterns" },
      "scope": { "type": "string", "enum": ["Project", "All", "Module", "Directory", "Scope"], "description": "Search scope", "default": "Project" },
      "scopeArg": { "type": "string", "description": "Required when scope is not Project" },
      "maxResults": { "type": "integer", "description": "Maximum number of matches", "default": 10, "minimum": 1 },
      "offset": { "type": "integer", "description": "Skip first N results", "default": 0, "minimum": 0 },
      "includeContext": { "type": "boolean", "description": "Include context lines", "default": false },
      "maxLineLength": { "type": "integer", "description": "Maximum line length", "default": 200, "minimum": 1 }
    },
    "required": ["query"]
  },
  "FileProblems": {
    "type": "object",
    "description": "Get static analysis results for a file.",
    "properties": {
      "filePath": { "type": "string", "description": "File path relative to project root" },
      "includeWarnings": { "type": "boolean", "description": "Include warnings", "default": true },
      "includeSuggestions": { "type": "boolean", "description": "Include suggestions", "default": false },
      "maxProblems": { "type": "integer", "description": "Maximum problems to return", "default": 50, "minimum": 1 }
    },
    "required": ["filePath"]
  },
  "FindUsages": {
    "type": "object",
    "description": "Find all usages/references of a symbol in the project.",
    "properties": {
      "filePath": { "type": "string", "description": "File path where the symbol is defined" },
      "symbolName": { "type": "string", "description": "Name of the symbol" },
      "line": { "type": "integer", "description": "Line number (1-based)", "minimum": 1 },
      "column": { "type": "integer", "description": "Column number (1-based)", "minimum": 1 },
      "symbolType": { "type": "string", "enum": ["Auto", "Class", "Method", "Field", "Variable", "Parameter", "File"], "default": "Auto" },
      "usageTypes": { "type": "array", "items": { "type": "string" }, "default": ["All"] },
      "searchScope": { "type": "string", "enum": ["Project", "Module", "Directory"], "default": "Project" },
      "maxResults": { "type": "integer", "description": "Maximum usages to return", "default": 20, "minimum": 1 },
      "offset": { "type": "integer", "default": 0, "minimum": 0 }
    },
    "required": ["filePath"]
  },
  "Rename": {
    "type": "object",
    "description": "Safely rename a symbol and update all references.",
    "properties": {
      "filePath": { "type": "string", "description": "File path where the symbol is defined" },
      "newName": { "type": "string", "description": "New name for the symbol" },
      "line": { "type": "integer", "description": "Line number (1-based)", "minimum": 1 },
      "column": { "type": "integer", "description": "Column number (1-based)", "minimum": 1 },
      "symbolType": { "type": "string", "enum": ["Auto", "Class", "Method", "Field", "Variable", "Parameter", "File"], "default": "Auto" },
      "searchInComments": { "type": "boolean", "default": true },
      "searchInStrings": { "type": "boolean", "default": false }
    },
    "required": ["filePath", "newName", "line"]
  }
}`,

    /**
     * JetBrains File MCP Tools Schema (JSON format)
     */
    JETBRAINS_FILE_TOOLS_SCHEMA: `{
  "ReadFile": {
    "type": "object",
    "description": "Read file content using IDE's VFS. Supports project files, JAR/ZIP entries, JDK sources, and .class files (auto-decompiled).",
    "properties": {
      "filePath": { "type": "string", "description": "File path. Supports relative paths, absolute paths, JAR paths" },
      "maxLines": { "type": "integer", "description": "Maximum lines to return", "default": 500, "minimum": 1, "maximum": 5000 },
      "offset": { "type": "integer", "description": "Line offset for pagination (0-based)", "default": 0, "minimum": 0 }
    },
    "required": ["filePath"]
  },
  "WriteFile": {
    "type": "object",
    "description": "Write content to a file. Creates if not exists, overwrites if exists.",
    "properties": {
      "filePath": { "type": "string", "description": "File path (relative to project root or absolute)" },
      "content": { "type": "string", "description": "Content to write" }
    },
    "required": ["filePath", "content"]
  },
  "EditFile": {
    "type": "object",
    "description": "Edit a file by replacing text. The oldString must be unique unless replaceAll is true.",
    "properties": {
      "filePath": { "type": "string", "description": "File path (relative or absolute)" },
      "oldString": { "type": "string", "description": "The text to replace" },
      "newString": { "type": "string", "description": "The replacement text" },
      "replaceAll": { "type": "boolean", "description": "Replace all occurrences", "default": false }
    },
    "required": ["filePath", "oldString", "newString"]
  }
}`,

    /**
     * User Interaction MCP Tools Schema (JSON format)
     */
    USER_INTERACTION_TOOLS_SCHEMA: `{
  "AskUserQuestion": {
    "type": "object",
    "description": "Ask the user questions and get their choices.",
    "properties": {
      "questions": {
        "type": "array",
        "description": "List of questions",
        "items": {
          "type": "object",
          "properties": {
            "question": { "type": "string", "description": "Question content" },
            "header": { "type": "string", "description": "Question header" },
            "options": { "type": "array", "items": { "type": "object", "properties": { "label": { "type": "string" }, "description": { "type": "string" } }, "required": ["label"] } },
            "multiSelect": { "type": "boolean", "description": "Allow multiple selections", "default": false }
          },
          "required": ["question", "header", "options"]
        }
      }
    },
    "required": ["questions"]
  }
}`,

    /**
     * Terminal MCP Tools Schema (JSON format)
     */
    TERMINAL_TOOLS_SCHEMA: `{
  "Terminal": {
    "type": "object",
    "description": "Execute commands in IDE's integrated terminal.",
    "properties": {
      "command": { "type": "string", "description": "The command to execute (required)" },
      "session_id": { "type": "string", "description": "Session ID to reuse" },
      "session_name": { "type": "string", "description": "Name for new terminal session" },
      "shell_type": { "type": "string", "description": "Shell type (dynamically detected)" },
      "wait": { "type": "boolean", "description": "Wait for completion", "default": true },
      "timeout": { "type": "integer", "description": "Timeout in seconds", "default": 30, "minimum": -1 }
    },
    "required": ["command"]
  },
  "TerminalRead": {
    "type": "object",
    "description": "Read output from a terminal session.",
    "properties": {
      "session_id": { "type": "string", "description": "Session ID to read from" },
      "max_lines": { "type": "integer", "description": "Maximum lines to return", "default": 1000, "minimum": 1 },
      "search": { "type": "string", "description": "Regex pattern to search" },
      "context_lines": { "type": "integer", "description": "Context lines around matches", "default": 2, "minimum": 0 },
      "wait": { "type": "boolean", "description": "Wait for command completion", "default": false },
      "timeout": { "type": "integer", "description": "Timeout in seconds", "default": 30, "minimum": -1 }
    },
    "required": []
  },
  "TerminalList": {
    "type": "object",
    "description": "List terminal sessions for the current AI session.",
    "properties": {
      "include_output_preview": { "type": "boolean", "description": "Include output preview", "default": false },
      "preview_lines": { "type": "integer", "description": "Lines for output preview", "default": 5, "minimum": 1 }
    },
    "required": []
  },
  "TerminalKill": {
    "type": "object",
    "description": "Close and destroy terminal session(s).",
    "properties": {
      "session_ids": { "type": "array", "items": { "type": "string" }, "description": "Session IDs to close" },
      "all": { "type": "boolean", "description": "Close all sessions", "default": false }
    },
    "required": []
  },
  "TerminalTypes": {
    "type": "object",
    "description": "Get available shell types for the current platform.",
    "properties": {},
    "required": []
  },
  "TerminalRename": {
    "type": "object",
    "description": "Rename a terminal session.",
    "properties": {
      "session_id": { "type": "string", "description": "Session ID to rename (required)" },
      "new_name": { "type": "string", "description": "New name (required)" }
    },
    "required": ["session_id", "new_name"]
  },
  "TerminalInterrupt": {
    "type": "object",
    "description": "Stop or pause the currently running command.",
    "properties": {
      "session_id": { "type": "string", "description": "Session ID to interrupt (required)" },
      "signal": { "type": "string", "enum": ["SIGINT", "SIGQUIT", "SIGTSTP"], "description": "Signal to send", "default": "SIGINT" }
    },
    "required": ["session_id"]
  }
}`,

    /**
     * Git MCP Tools Schema (JSON format)
     */
    GIT_TOOLS_SCHEMA: `{
  "GetVcsChanges": {
    "type": "object",
    "description": "Get uncommitted VCS changes in the current project.",
    "properties": {
      "selectedOnly": { "type": "boolean", "description": "Only return selected files", "default": false },
      "includeDiff": { "type": "boolean", "description": "Include diff content", "default": true },
      "maxFiles": { "type": "integer", "description": "Maximum files to return", "default": 50, "minimum": 1 },
      "maxDiffLines": { "type": "integer", "description": "Maximum diff lines per file", "default": 100, "minimum": 1 }
    },
    "required": []
  },
  "GetCommitMessage": {
    "type": "object",
    "description": "Get the current commit message from the panel.",
    "properties": {},
    "required": []
  },
  "SetCommitMessage": {
    "type": "object",
    "description": "Set or append to the commit message.",
    "properties": {
      "message": { "type": "string", "description": "The commit message to set" },
      "mode": { "type": "string", "enum": ["replace", "append"], "description": "Write mode", "default": "replace" }
    },
    "required": ["message"]
  },
  "GetVcsStatus": {
    "type": "object",
    "description": "Get VCS status overview.",
    "properties": {},
    "required": []
  },
  "SelectFiles": {
    "type": "object",
    "description": "Select files in the Commit panel.",
    "properties": {
      "paths": { "type": "array", "items": { "type": "string" }, "description": "File paths to select" },
      "mode": { "type": "string", "enum": ["replace", "add"], "description": "Selection mode", "default": "add" }
    },
    "required": ["paths"]
  },
  "DeselectFiles": {
    "type": "object",
    "description": "Deselect files from the Commit panel.",
    "properties": {
      "paths": { "type": "array", "items": { "type": "string" }, "description": "File paths to deselect" }
    },
    "required": ["paths"]
  },
  "SelectAllFiles": {
    "type": "object",
    "description": "Select all changed files.",
    "properties": {},
    "required": []
  },
  "DeselectAllFiles": {
    "type": "object",
    "description": "Deselect all files.",
    "properties": {},
    "required": []
  },
  "CommitChanges": {
    "type": "object",
    "description": "Commit selected files to the repository.",
    "properties": {
      "message": { "type": "string", "description": "Commit message" },
      "amend": { "type": "boolean", "description": "Amend previous commit", "default": false },
      "push": { "type": "boolean", "description": "Push after committing", "default": false }
    },
    "required": []
  }
}`,

    // Default instructions - these would typically be loaded from resources
    // For VS Code, we provide default English instructions
    get USER_INTERACTION_INSTRUCTIONS() {
        return 'Use the AskUserQuestion tool to interact with users when you need their input or confirmation for decisions.';
    },

    get JETBRAINS_INSTRUCTIONS() {
        return 'Use JetBrains IDE tools for fast code search, symbol lookup, and project navigation. These tools leverage IDE indexing for better performance than file system searches.';
    },

    get JETBRAINS_FILE_INSTRUCTIONS() {
        return 'Use JetBrains File MCP for reading, writing, and editing files. Supports project files, JAR contents, and auto-decompilation of .class files.';
    },

    get CONTEXT7_INSTRUCTIONS() {
        return 'Use Context7 MCP to fetch up-to-date documentation for libraries and frameworks.';
    },

    get TERMINAL_INSTRUCTIONS() {
        return 'Use Terminal MCP to execute commands in the integrated terminal. Commands run in isolated sessions that can be managed independently.';
    },

    get GIT_INSTRUCTIONS() {
        return 'Use Git MCP to interact with version control. Get uncommitted changes, manage commit messages, and commit code.';
    }
};

/**
 * Known Tools Lists (for auto-completion)
 */
export const KnownTools = {
    /**
     * Claude Code built-in tools
     */
    CLAUDE_BUILT_IN: [
        'Read',           // Read file
        'Write',          // Write file
        'Edit',           // Edit file
        'Glob',           // File pattern matching
        'Grep',           // Search file content
        'Bash',           // Execute commands
        'Task',           // Start sub-agent
        'TodoWrite',      // Task management
        'WebFetch',       // Fetch web content
        'WebSearch',      // Web search
        'NotebookEdit',   // Jupyter notebook edit
        'AskUserQuestion' // Ask user
    ],

    /**
     * JetBrains MCP tools
     */
    JETBRAINS_MCP: [
        'mcp__jetbrains__FileIndex',
        'mcp__jetbrains__CodeSearch',
        'mcp__jetbrains__DirectoryTree',
        'mcp__jetbrains__FileProblems',
        'mcp__jetbrains__FindUsages',
        'mcp__jetbrains__Rename',
        'mcp__jetbrains__ReadFile',
        'mcp__jetbrains__WriteFile',
        'mcp__jetbrains__EditFile'
    ],

    /**
     * Terminal MCP tools
     */
    TERMINAL_MCP: [
        'mcp__terminal__Terminal',
        'mcp__terminal__TerminalRead',
        'mcp__terminal__TerminalList',
        'mcp__terminal__TerminalKill',
        'mcp__terminal__TerminalTypes',
        'mcp__terminal__TerminalRename'
    ],

    /**
     * Git MCP tools
     */
    GIT_MCP: [
        'mcp__jetbrains_git__GetVcsChanges',
        'mcp__jetbrains_git__GetCommitMessage',
        'mcp__jetbrains_git__SetCommitMessage',
        'mcp__jetbrains_git__GetVcsStatus',
        'mcp__jetbrains_git__SelectFiles',
        'mcp__jetbrains_git__DeselectFiles',
        'mcp__jetbrains_git__SelectAllFiles',
        'mcp__jetbrains_git__DeselectAllFiles',
        'mcp__jetbrains_git__CommitChanges'
    ],

    /**
     * All known tools
     */
    get ALL() {
        return [
            ...this.CLAUDE_BUILT_IN,
            ...this.JETBRAINS_MCP,
            ...this.TERMINAL_MCP,
            ...this.GIT_MCP
        ];
    }
};

/**
 * Agent Default Configurations
 */
export const AgentDefaults = {
    /**
     * ExploreWithJetbrains Agent default configuration
     */
    EXPLORE_WITH_JETBRAINS: {
        name: 'ExploreWithJetbrains',
        description: 'Code exploration agent leveraging JetBrains IDE indexing capabilities. Use for fast file/class/symbol search and code structure analysis.',
        selectionHint: `- \`ExploreWithJetbrains\`: Code exploration agent leveraging JetBrains IDE indexing capabilities. Use for fast file/class/symbol search and code structure analysis.

This agent provides faster and more accurate results than default exploration because it uses IDE's pre-built indexes.

IMPORTANT: For code exploration tasks, prefer \`subagent_type="ExploreWithJetbrains"\` over the default \`Explore\` agent.`,
        prompt: `You are a code exploration expert, skilled at leveraging JetBrains IDE's powerful indexing capabilities to quickly locate and analyze code.

## Tool Usage Strategy

### Prefer JetBrains Tools (Faster & More Accurate)
- **jetbrains / FileIndex**: Search file names, class names, symbol names
- **jetbrains / CodeSearch**: Search code content in project
- **jetbrains / DirectoryTree**: Quickly understand directory structure
- **jetbrains / FileProblems**: Get static analysis results for files

### Standard Tools
- **Read**: Read full file content (when viewing specific code)

## Workflow
1. **Understand Goal**: Clarify what the user wants to explore
2. **Choose Tool**: Select the most appropriate tool based on task type
3. **Progressive Depth**: From overview to details
4. **Summarize Findings**: Return concise, valuable results`,
        tools: [
            'Read',
            'mcp__jetbrains__FileIndex',
            'mcp__jetbrains__CodeSearch',
            'mcp__jetbrains__DirectoryTree',
            'mcp__jetbrains__FileProblems',
            'mcp__jetbrains__ReadFile'
        ]
    } as AgentConfig
};

/**
 * Git Generate Feature Defaults
 */
export const GitGenerateDefaults = {
    /**
     * Default system prompt
     */
    SYSTEM_PROMPT: `You are a commit message generator integrated with the IDE.

## Available Tools
- **GetVcsChanges**: Get uncommitted file changes with diff content
- **SetCommitMessage**: Set the commit message in IDE's commit panel
- **GetVcsStatus**: Get current VCS status (branch, change counts)
- **Read**: Read file content to understand code context

## Workflow
1. Call GetVcsChanges(selectedOnly=true, includeDiff=true) to get code changes
2. If the diff is unclear, use Read tool to examine full file content
3. Analyze changes and understand purpose/impact
4. Generate commit message following conventional commits format
5. **MUST** call SetCommitMessage to fill the message into IDE's commit panel

## Commit Message Format
\`\`\`
<type>[optional scope]: <description>

[optional body]

[optional footer(s)]
\`\`\`

## Types
- **feat**: A new feature
- **fix**: A bug fix
- **docs**: Documentation only changes
- **style**: Changes that do not affect the meaning of the code
- **refactor**: A code change that neither fixes a bug nor adds a feature
- **perf**: A code change that improves performance
- **test**: Adding missing tests or correcting existing tests
- **build**: Changes that affect the build system
- **ci**: Changes to CI configuration
- **chore**: Other changes that don't modify src or test files

IMPORTANT: You MUST call SetCommitMessage tool to set the result.`,

    /**
     * Default user prompt (runtime)
     */
    USER_PROMPT: `Analyze the following code changes and generate an appropriate commit message.

Focus on:
1. What functionality was added, changed, or removed
2. Why the change was made (if apparent from the diff)
3. Any breaking changes or important notes

Use tools only - do not output the commit message as text.`,

    /**
     * Default allowed tools
     */
    TOOLS: [
        'mcp__jetbrains_git__GetVcsChanges',
        'mcp__jetbrains_git__GetCommitMessage',
        'mcp__jetbrains_git__SetCommitMessage',
        'mcp__jetbrains_git__GetVcsStatus',
        'Read',
        'mcp__jetbrains__FileIndex',
        'mcp__jetbrains__CodeSearch',
        'mcp__jetbrains__DirectoryTree',
        'mcp__jetbrains__FileProblems'
    ]
};

/**
 * Codex mode auto-approved tools defaults
 * 
 * These tools can execute without user confirmation in Codex mode.
 * High-risk operations (write files, execute commands, commit code) require confirmation.
 */
export const McpAutoApprovedDefaults = {
    /**
     * JetBrains File MCP default auto-approved tools
     * - ReadFile: Read-only, safe
     * - WriteFile/EditFile: Write operations, require confirmation
     */
    JETBRAINS_FILE: ['ReadFile'],

    /**
     * JetBrains LSP MCP default auto-approved tools
     * All are read-only queries or safe refactoring operations
     */
    JETBRAINS_LSP: [
        'DirectoryTree',
        'FileProblems',
        'FileIndex',
        'CodeSearch',
        'FindUsages',
        'Rename'  // IDE safe refactoring with preview and undo
    ],

    /**
     * JetBrains Terminal MCP default auto-approved tools
     * - Terminal: Execute commands, high-risk, require confirmation
     * - Others: Read/manage terminal sessions, safe
     */
    JETBRAINS_TERMINAL: [
        'TerminalRead',
        'TerminalList',
        'TerminalKill',
        'TerminalTypes',
        'TerminalRename',
        'TerminalInterrupt'
        // Terminal not in list, requires user confirmation
    ],

    /**
     * JetBrains Git MCP default auto-approved tools
     * - CommitChanges: Commit code, high-risk, require confirmation
     * - Others: Query/prepare commit, safe
     */
    JETBRAINS_GIT: [
        'GetVcsChanges',
        'GetCommitMessage',
        'SetCommitMessage',
        'GetVcsStatus',
        'SelectFiles',
        'DeselectFiles',
        'SelectAllFiles',
        'DeselectAllFiles'
        // CommitChanges not in list, requires user confirmation
    ],

    /**
     * User Interaction MCP default auto-approved tools
     * AskUserQuestion is user interaction itself, auto-approved
     */
    USER_INTERACTION: ['AskUserQuestion']
};

export default McpDefaults;
