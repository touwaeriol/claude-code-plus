package com.claudecodeplus.plugin.types

/**
 * 工具类型常量
 * 
 * 对应 frontend/src/constants/toolTypes.ts
 */
object ToolConstants {
    // 文件操作工具
    const val READ = "Read"
    const val WRITE = "Write"
    const val EDIT = "Edit"
    const val MULTI_EDIT = "MultiEdit"
    const val NOTEBOOK_EDIT = "NotebookEdit"
    
    // 命令执行工具
    const val BASH = "Bash"
    const val BASH_OUTPUT = "BashOutput"
    const val TASK = "Task"
    const val KILL_SHELL = "KillShell"
    
    // 搜索工具
    const val GREP = "Grep"
    const val GLOB = "Glob"
    
    // Web 工具
    const val WEB_SEARCH = "WebSearch"
    const val WEB_FETCH = "WebFetch"
    
    // 任务和计划工具
    const val TODO_WRITE = "TodoWrite"
    const val EXIT_PLAN_MODE = "ExitPlanMode"
    
    // 用户交互工具
    const val ASK_USER_QUESTION = "AskUserQuestion"
    
    // 技能和命令工具
    const val SKILL = "Skill"
    const val SLASH_COMMAND = "SlashCommand"
    
    // MCP 工具
    const val LIST_MCP_RESOURCES = "ListMcpResourcesTool"
    const val READ_MCP_RESOURCE = "ReadMcpResourceTool"
    
    // 工具名称映射
    val TOOL_NAME_TO_TYPE = mapOf(
        "Read" to READ,
        "Write" to WRITE,
        "Edit" to EDIT,
        "MultiEdit" to MULTI_EDIT,
        "NotebookEdit" to NOTEBOOK_EDIT,
        "Bash" to BASH,
        "BashOutput" to BASH_OUTPUT,
        "Task" to TASK,
        "KillShell" to KILL_SHELL,
        "Grep" to GREP,
        "Glob" to GLOB,
        "WebSearch" to WEB_SEARCH,
        "WebFetch" to WEB_FETCH,
        "TodoWrite" to TODO_WRITE,
        "ExitPlanMode" to EXIT_PLAN_MODE,
        "AskUserQuestion" to ASK_USER_QUESTION,
        "Skill" to SKILL,
        "SlashCommand" to SLASH_COMMAND,
        "ListMcpResourcesTool" to LIST_MCP_RESOURCES,
        "ReadMcpResourceTool" to READ_MCP_RESOURCE
    )
}


