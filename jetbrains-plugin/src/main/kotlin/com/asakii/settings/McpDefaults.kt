package com.asakii.settings

/**
 * MCP 配置默认值
 *
 * 存储内置 MCP 服务器的默认系统提示词、工具 schema 和配置
 */
object McpDefaults {

    /**
     * Context7 MCP 服务器配置
     */
    object Context7Server {
        const val URL = "https://mcp.context7.com/mcp"
        const val API_KEY_HEADER = "CONTEXT7_API_KEY"
        const val DESCRIPTION = "Context7 MCP - Fetch up-to-date documentation for libraries"
    }

    /**
     * JetBrains MCP 工具 Schema（JSON 格式）
     */
    val JETBRAINS_TOOLS_SCHEMA = """
{
  "FileIndex": {
    "type": "object",
    "description": "Search files, classes, and symbols in the IDE index by keywords. Faster than file system search, supports fuzzy matching.",
    "properties": {
      "query": {
        "type": "string",
        "description": "Search keywords"
      },
      "searchType": {
        "type": "string",
        "enum": ["All", "Classes", "Files", "Symbols", "Actions", "Text"],
        "description": "Search type",
        "default": "All"
      },
      "scope": {
        "type": "string",
        "enum": ["Project", "All", "ProductionFiles", "TestFiles", "Scratches"],
        "description": "Search scope",
        "default": "Project"
      },
      "maxResults": {
        "type": "integer",
        "description": "Max results",
        "default": 20,
        "minimum": 1
      },
      "offset": {
        "type": "integer",
        "description": "Offset",
        "default": 0,
        "minimum": 0
      }
    },
    "required": ["query"]
  },

  "DirectoryTree": {
    "type": "object",
    "description": "Get the tree structure of the project directory. Supports depth limit, file filtering, and hidden files options.",
    "properties": {
      "path": {
        "type": "string",
        "description": "Path relative to project root (e.g. \"src/main\", \"frontend/src\")",
        "default": "."
      },
      "maxDepth": {
        "type": "integer",
        "description": "Maximum recursion depth. Use -1 or 0 for unlimited depth.",
        "default": 3
      },
      "filesOnly": {
        "type": "boolean",
        "description": "Show only files, hide directory entries",
        "default": false
      },
      "includeHidden": {
        "type": "boolean",
        "description": "Include hidden files/directories (names starting with .)",
        "default": false
      },
      "pattern": {
        "type": "string",
        "description": "File name filter using glob patterns. Examples: \"*.kt\" (Kotlin files), \"*.{ts,vue}\" (TypeScript and Vue), \"Test*\" (files starting with Test)"
      },
      "maxEntries": {
        "type": "integer",
        "description": "Maximum number of entries to return (prevents overwhelming output for large directories)",
        "default": 100,
        "minimum": 1
      }
    },
    "required": []
  },

  "CodeSearch": {
    "type": "object",
    "description": "Search code content across project files (like IDE's Find in Files). Uses IDEA's indexing for fast searches.",
    "properties": {
      "query": {
        "type": "string",
        "description": "Search text or regular expression pattern to find in file contents"
      },
      "isRegex": {
        "type": "boolean",
        "description": "Treat query as regular expression (e.g. \"log.*Error\", \"function\\s+\\w+\")",
        "default": false
      },
      "caseSensitive": {
        "type": "boolean",
        "description": "Case sensitive search",
        "default": false
      },
      "wholeWords": {
        "type": "boolean",
        "description": "Match whole words only (not substrings)",
        "default": false
      },
      "fileMask": {
        "type": "string",
        "description": "File name filter using glob patterns. Examples: \"*.vue\" (Vue files), \"*.kt,*.java\" (multiple types)"
      },
      "scope": {
        "type": "string",
        "enum": ["Project", "All", "Module", "Directory", "Scope"],
        "description": "Search scope",
        "default": "Project"
      },
      "scopeArg": {
        "type": "string",
        "description": "Required when scope is not Project. For Module: module name. For Directory: relative path."
      },
      "maxResults": {
        "type": "integer",
        "description": "Maximum number of matches to return",
        "default": 10,
        "minimum": 1
      },
      "offset": {
        "type": "integer",
        "description": "Skip first N results (for pagination)",
        "default": 0,
        "minimum": 0
      },
      "includeContext": {
        "type": "boolean",
        "description": "Include one line before and after each match for context",
        "default": false
      },
      "maxLineLength": {
        "type": "integer",
        "description": "Maximum length of line content in results",
        "default": 200,
        "minimum": 1
      }
    },
    "required": ["query"]
  },

  "FileProblems": {
    "type": "object",
    "description": "Get static analysis results for a file, including compilation errors, warnings and code inspection issues.",
    "properties": {
      "filePath": {
        "type": "string",
        "description": "File path relative to project root"
      },
      "includeWarnings": {
        "type": "boolean",
        "description": "Include warnings",
        "default": true
      },
      "includeSuggestions": {
        "type": "boolean",
        "description": "Include suggestions/weak warnings",
        "default": false
      },
      "includeWeakWarnings": {
        "type": "boolean",
        "description": "Deprecated: use includeSuggestions instead",
        "default": false
      },
      "maxProblems": {
        "type": "integer",
        "description": "Maximum number of problems to return",
        "default": 50,
        "minimum": 1
      }
    },
    "required": ["filePath"]
  },

  "FindUsages": {
    "type": "object",
    "description": "Find all usages/references of a symbol in the project. Similar to IDE's Find Usages (Alt+F7) feature.",
    "properties": {
      "filePath": {
        "type": "string",
        "description": "File path where the symbol is defined"
      },
      "symbolName": {
        "type": "string",
        "description": "Name of the symbol to find usages for"
      },
      "line": {
        "type": "integer",
        "description": "Line number where the symbol is located (1-based)",
        "minimum": 1
      },
      "column": {
        "type": "integer",
        "description": "Column number where the symbol is located (1-based)",
        "minimum": 1
      },
      "symbolType": {
        "type": "string",
        "enum": ["Auto", "Class", "Method", "Field", "Variable", "Parameter", "File"],
        "description": "Type of symbol to search for",
        "default": "Auto"
      },
      "usageTypes": {
        "type": "array",
        "items": {
          "type": "string",
          "enum": ["All", "Inheritance", "Instantiation", "TypeReference", "Import", "Override", "Call", "MethodReference", "Read", "Write"]
        },
        "description": "Filter by usage types",
        "default": ["All"]
      },
      "searchScope": {
        "type": "string",
        "enum": ["Project", "Module", "Directory"],
        "description": "Search scope",
        "default": "Project"
      },
      "scopeArg": {
        "type": "string",
        "description": "Required when searchScope is Module or Directory"
      },
      "maxResults": {
        "type": "integer",
        "description": "Maximum number of usages to return",
        "default": 20,
        "minimum": 1
      },
      "offset": {
        "type": "integer",
        "description": "Skip first N results (for pagination)",
        "default": 0,
        "minimum": 0
      }
    },
    "required": ["filePath"]
  },

  "Rename": {
    "type": "object",
    "description": "Safely rename a symbol and automatically update all references. Similar to IDE's Refactor > Rename (Shift+F6).",
    "properties": {
      "filePath": {
        "type": "string",
        "description": "File path where the symbol is defined"
      },
      "newName": {
        "type": "string",
        "description": "New name for the symbol"
      },
      "line": {
        "type": "integer",
        "description": "Line number where the symbol is located (1-based)",
        "minimum": 1
      },
      "column": {
        "type": "integer",
        "description": "Column number where the symbol is located (1-based)",
        "minimum": 1
      },
      "symbolType": {
        "type": "string",
        "enum": ["Auto", "Class", "Method", "Field", "Variable", "Parameter", "File"],
        "description": "Type of symbol to rename",
        "default": "Auto"
      },
      "searchInComments": {
        "type": "boolean",
        "description": "Also rename occurrences in comments",
        "default": true
      },
      "searchInStrings": {
        "type": "boolean",
        "description": "Also rename occurrences in string literals",
        "default": false
      }
    },
    "required": ["filePath", "newName", "line"]
  }
}
    """.trimIndent()

    /**
     * User Interaction MCP tool Schema (JSON format)
     */
    val USER_INTERACTION_TOOLS_SCHEMA = """
{
  "AskUserQuestion": {
    "type": "object",
    "description": "Ask the user questions and get their choices. Use this tool to interact with users when input or confirmation is needed.",
    "properties": {
      "questions": {
        "type": "array",
        "description": "List of questions",
        "items": {
          "type": "object",
          "properties": {
            "question": {
              "type": "string",
              "description": "Question content"
            },
            "header": {
              "type": "string",
              "description": "Question header/category label"
            },
            "options": {
              "type": "array",
              "description": "List of options",
              "items": {
                "type": "object",
                "properties": {
                  "label": {
                    "type": "string",
                    "description": "Option display text"
                  },
                  "description": {
                    "type": "string",
                    "description": "Option description (optional)"
                  }
                },
                "required": ["label"]
              }
            },
            "multiSelect": {
              "type": "boolean",
              "description": "Allow multiple selections, default false"
            }
          },
          "required": ["question", "header", "options"]
        }
      }
    },
    "required": ["questions"]
  }
}
    """.trimIndent()

    /**
     * User Interaction MCP 默认提示词
     */
    const val USER_INTERACTION_INSTRUCTIONS = """When you need clarification from the user, especially when presenting multiple options or choices, use the `mcp__user_interaction__AskUserQuestion` tool to ask questions. The user's response will be returned to you through this tool."""

    /**
     * JetBrains IDE MCP 默认提示词
     */
    val JETBRAINS_INSTRUCTIONS = """
### MCP Tools

You have access to JetBrains IDE tools that leverage the IDE's powerful indexing and analysis capabilities:

- `mcp__jetbrains__DirectoryTree`: Browse project directory structure with filtering options
- `mcp__jetbrains__FileProblems`: Get static analysis results for a file (syntax errors, code errors, warnings, suggestions)
- `mcp__jetbrains__FileIndex`: Search files, classes, and symbols using IDE index (supports scope filtering)
- `mcp__jetbrains__CodeSearch`: Search code content across project files (like Find in Files)
- `mcp__jetbrains__FindUsages`: Find all references/usages of a symbol (class, method, field, variable) in the project
- `mcp__jetbrains__Rename`: Safely rename a symbol and automatically update all references (like Refactor > Rename)

CRITICAL: You MUST use JetBrains tools instead of Glob/Grep. DO NOT use Glob or Grep unless JetBrains tools fail or are unavailable:
- ALWAYS use `mcp__jetbrains__CodeSearch` instead of `Grep` for searching code content
- ALWAYS use `mcp__jetbrains__FileIndex` instead of `Glob` for finding files, classes, and symbols
- Only fall back to Glob/Grep if JetBrains tools return errors or cannot handle the specific query

IMPORTANT: After completing code modifications, you MUST use `mcp__jetbrains__FileProblems` to perform static analysis validation on the modified files to minimize syntax errors.

### Refactoring Workflow

When renaming symbols:
1. Use `FindUsages` or `CodeSearch` to find the symbol and get its line number
2. Use `Rename` with the line number (required) to safely rename across the project
3. Use `FileProblems` to validate changes

Example: `FindUsages(symbolName="getUserById")` → line 42 → `Rename(line=42, newName="fetchUserById")`

**Note**: `Rename` requires `line` parameter for precise location. Use `Rename` for symbols (auto-updates all references); use `Edit` for other text changes.

### Subagents

- `ExploreWithJetbrains`: Code exploration agent leveraging JetBrains IDE indexing capabilities. Use for fast file/class/symbol search and code structure analysis. Prefer this when exploring or understanding codebases. (Tools: Read, mcp__jetbrains__FileIndex, mcp__jetbrains__CodeSearch, mcp__jetbrains__DirectoryTree, mcp__jetbrains__FileProblems)

This agent provides faster and more accurate results than default exploration because it uses IDE's pre-built indexes.

IMPORTANT: For code exploration tasks, prefer `subagent_type="ExploreWithJetbrains"` over the default `Explore` agent. When invoking with Task tool, the `description` parameter is required.
    """.trimIndent()

    /**
     * Context7 MCP 默认提示词
     */
    val CONTEXT7_INSTRUCTIONS = """
# Context7 MCP

IMPORTANT: When working with third-party libraries, ALWAYS query Context7 first to get up-to-date documentation and prevent hallucinated APIs.

## Tools

- `resolve-library-id`: Resolve library name → Context7 ID. Call first unless user provides `/org/project` format.
- `get-library-docs`: Fetch documentation.
  - `mode`: `code` (API/examples) | `info` (concepts/guides)
  - `topic`: Focus area (e.g., "hooks", "routing", "authentication")
  - `page`: Pagination (1-10) if context insufficient
    """.trimIndent()
}

/**
 * 已知工具列表（用于自动补全）
 */
object KnownTools {
    /**
     * Claude Code 内置工具
     */
    val CLAUDE_BUILT_IN = listOf(
        "Read",           // 读取文件
        "Write",          // 写入文件
        "Edit",           // 编辑文件
        "Glob",           // 文件模式匹配
        "Grep",           // 搜索文件内容
        "Bash",           // 执行命令
        "Task",           // 启动子代理
        "TodoWrite",      // 任务管理
        "WebFetch",       // 获取网页内容
        "WebSearch",      // 网络搜索
        "NotebookEdit",   // Jupyter notebook 编辑
        "AskUserQuestion" // 询问用户
    )

    /**
     * JetBrains MCP 工具
     */
    val JETBRAINS_MCP = listOf(
        "mcp__jetbrains__FileIndex",      // IDE 索引搜索
        "mcp__jetbrains__CodeSearch",     // 代码内容搜索
        "mcp__jetbrains__DirectoryTree",  // 目录结构
        "mcp__jetbrains__FileProblems",   // 静态分析
        "mcp__jetbrains__FindUsages",     // 查找引用
        "mcp__jetbrains__Rename"          // 重命名重构
    )

    /**
     * 所有已知工具
     */
    val ALL = CLAUDE_BUILT_IN + JETBRAINS_MCP
}

/**
 * Agent 配置默认值
 */
object AgentDefaults {

    /**
     * ExploreWithJetbrains Agent 默认配置
     */
    val EXPLORE_WITH_JETBRAINS = AgentConfig(
        name = "ExploreWithJetbrains",
        description = "Code exploration agent leveraging JetBrains IDE indexing capabilities. Use for fast file/class/symbol search and code structure analysis. Prefer this when exploring or understanding codebases.",
        selectionHint = """
- `ExploreWithJetbrains`: Code exploration agent leveraging JetBrains IDE indexing capabilities. Use for fast file/class/symbol search and code structure analysis. Prefer this when exploring or understanding codebases. (Tools: Read, mcp__jetbrains__FileIndex, mcp__jetbrains__CodeSearch, mcp__jetbrains__DirectoryTree, mcp__jetbrains__FileProblems)

This agent provides faster and more accurate results than default exploration because it uses IDE's pre-built indexes.

IMPORTANT: For code exploration tasks, prefer `subagent_type="ExploreWithJetbrains"` over the default `Explore` agent. When invoking with Task tool, the `description` parameter is required.
        """.trimIndent(),
        prompt = """
You are a code exploration expert, skilled at leveraging JetBrains IDE's powerful indexing capabilities to quickly locate and analyze code.

## Tool Usage Strategy

### Prefer JetBrains Tools (Faster & More Accurate)

- **mcp__jetbrains__FileIndex**: Search file names, class names, symbol names
  - Faster than Glob, uses IDE pre-built index
  - Supports fuzzy matching
  - Best for finding class definitions, file locations

- **mcp__jetbrains__CodeSearch**: Search code content in project
  - Similar to IDE's "Find in Files" feature
  - Supports regex, case-sensitive, whole word matching
  - More accurate than Grep, leverages IDE index

- **mcp__jetbrains__DirectoryTree**: Quickly understand directory structure
  - Supports depth limits, file filtering
  - More efficient than ls or find

- **mcp__jetbrains__FileProblems**: Get static analysis results for files
  - Categories: syntax errors, code errors, warnings, suggestions
  - Leverages IDE's real-time analysis capability

### Fallback to Standard Tools

- **Read**: Read full file content (when viewing specific code)
- **Grep**: When complex regex matching needed or JetBrains tools don't apply
- **Glob**: When complex file pattern matching needed

## Workflow

1. **Understand Goal**: Clarify what the user wants to explore
2. **Choose Tool**: Select the most appropriate tool based on task type
   - Find files/classes/symbols -> FileIndex
   - Search code content -> CodeSearch
   - Understand directory structure -> DirectoryTree
   - View specific code -> Read
3. **Progressive Depth**: From overview to details
4. **Summarize Findings**: Return concise, valuable results

## Output Requirements

- Only return information relevant to user's question
- Provide file paths and line numbers for easy navigation
- Summarize findings rather than listing all search results
- If too many results, provide overview first then detail key parts
        """.trimIndent(),
        tools = listOf(
            "Read", "Grep", "Glob",
            "mcp__jetbrains__FileIndex",
            "mcp__jetbrains__CodeSearch",
            "mcp__jetbrains__DirectoryTree",
            "mcp__jetbrains__FileProblems"
        )
    )
}

/**
 * Agent 配置数据类
 */
data class AgentConfig(
    val name: String,
    val description: String,
    val prompt: String,
    val tools: List<String>,
    val selectionHint: String = "" // 主 AI 的子代理选择指引
)
