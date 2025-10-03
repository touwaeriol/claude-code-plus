package com.claudecodeplus.ui.viewmodels.tool

/**
 * UI 层工具类型枚举
 *
 * 独立于 SDK，避免 UI 层直接依赖 SDK 枚举类型。
 * 这样可以在 SDK 变化时，只需修改 Mapper 层，UI 层保持不变。
 *
 * 支持的工具类型：
 * - 文件操作：READ, EDIT, MULTI_EDIT, WRITE, NOTEBOOK_EDIT
 * - 搜索类：GLOB, GREP
 * - 命令执行：BASH, BASH_OUTPUT, KILL_SHELL
 * - 任务管理：TASK, TODO_WRITE
 * - 网络类：WEB_FETCH, WEB_SEARCH
 * - MCP：MCP, LIST_MCP_RESOURCES, READ_MCP_RESOURCE
 * - 其他：EXIT_PLAN_MODE, SLASH_COMMAND, UNKNOWN
 */
enum class UiToolType {
    /** Bash 命令执行 */
    BASH,

    /** 文件编辑（单次替换） */
    EDIT,

    /** 文件批量编辑 */
    MULTI_EDIT,

    /** 文件读取 */
    READ,

    /** 文件写入 */
    WRITE,

    /** 文件模式匹配（glob pattern） */
    GLOB,

    /** 内容搜索（grep） */
    GREP,

    /** 待办事项管理 */
    TODO_WRITE,

    /** 子任务代理 */
    TASK,

    /** 网页抓取 */
    WEB_FETCH,

    /** 网络搜索 */
    WEB_SEARCH,

    /** Jupyter Notebook 编辑 */
    NOTEBOOK_EDIT,

    /** MCP 工具调用 */
    MCP,

    /** 获取后台 Bash 输出 */
    BASH_OUTPUT,

    /** 终止后台 Shell 进程 */
    KILL_SHELL,

    /** 退出计划模式 */
    EXIT_PLAN_MODE,

    /** 列出 MCP 资源 */
    LIST_MCP_RESOURCES,

    /** 读取 MCP 资源 */
    READ_MCP_RESOURCE,

    /** 自定义斜杠命令 */
    SLASH_COMMAND,

    /** 未知类型（兜底） */
    UNKNOWN
}

/**
 * IDE 集成类型
 *
 * 定义工具调用在 IDE 中的交互方式：
 * - SHOW_DIFF: 显示文件修改前后对比（用于 EDIT, MULTI_EDIT）
 * - OPEN_FILE: 在编辑器中打开文件（用于 READ, WRITE）
 */
enum class IdeIntegrationType {
    /** 在 IDE 中显示 Diff 对比视图 */
    SHOW_DIFF,

    /** 在 IDE 编辑器中打开文件 */
    OPEN_FILE
}
