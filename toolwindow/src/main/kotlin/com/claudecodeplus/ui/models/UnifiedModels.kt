package com.claudecodeplus.ui.models

import java.util.UUID

/**
 * AI 模型枚举 - 定义可用的 Claude 模型
 * 
 * 枚举所有支持的 AI 模型，每个模型都有特定的能力和适用场景。
 * 
 * 属性说明：
 * - displayName: UI 显示名称，用户可见
 * - cliName: Claude CLI 使用的模型标识符
 * - description: 模型特点简要描述
 * 
 * 模型选择建议：
 * - OPUS: 适合复杂的编程任务、深度分析和架构设计
 * - SONNET: 适合日常编码、代码审查和一般问题解答
 */
enum class AiModel(
    val displayName: String, 
    val cliName: String, 
    val description: String,
    val contextLength: Int, // 上下文长度（tokens）
    val shortName: String, // 简短名称，用于紧凑显示
    val icon: String // 图标，用于最紧凑显示
) {
    // 默认模型 - 使用系统默认配置
    DEFAULT("Default", "default", "Use system default model", 200_000, "Default", "🔘"),
    
    // Opus 模型 - 最强大的模型，用于复杂任务
    OPUS("Opus", "opus", "Deep reasoning, complex tasks", 200_000, "Opus", "🔹"),
    
    // Sonnet 模型 - 平衡型模型，速度和能力兼备
    SONNET("Sonnet", "sonnet", "Balanced performance, daily coding", 200_000, "Sonnet", "🔷"),
    
    // Plan 模式 - 仅规划不执行
    OPUS_PLAN("Opus Plan", "opusplan", "Planning mode, analysis only", 200_000, "Plan", "📝"),
    
    // 最新版 Opus 4 - 特定版本
    OPUS_4("Opus 4", "claude-opus-4-20250514", "Latest Opus 4 model", 200_000, "O4", "✨")
}

/**
 * 权限模式枚举 - 控制 AI 执行操作的权限级别
 * 
 * 定义 AI 在执行文件操作、命令执行等时的权限模式。
 * 
 * 属性说明：
 * - displayName: UI 显示名称
 * - cliName: Claude CLI 使用的参数值
 * - description: 模式描述
 * 
 * 安全建议：
 * - 生产环境使用 DEFAULT 或 ACCEPT_EDITS
 * - 开发环境可使用 BYPASS_PERMISSIONS 提高效率
 * - PLAN 模式用于预览 AI 的操作计划
 */
enum class PermissionMode(
    val displayName: String, 
    val cliName: String, 
    val description: String,
    val shortName: String, // 简短名称，用于紧凑显示
    val icon: String // 图标，用于最紧凑显示
) {
    // 默认模式 - 每次操作都需要用户确认
    DEFAULT("Default", "default", "Default permission mode", "Default", "🔒"),
    
    // 接受编辑模式 - 自动接受文件编辑，但其他操作仍需确认  
    ACCEPT("Accept Edits", "acceptEdits", "Auto-accept edit operations", "Accept", "✅"),
    
    // 绕过权限模式 - 所有操作都不需要确认（注意安全）
    BYPASS("Bypass", "bypassPermissions", "Bypass permission checks", "Bypass", "🚀"),
    
    // 计划模式 - AI 只生成操作计划，不实际执行
    PLAN("Plan", "plan", "Planning only, no execution", "Plan", "📋")
}

/**
 * 消息角色 - 标识消息的发送者
 * 
 * 定义对话中不同类型的参与者。
 * 
 * 角色说明：
 * - USER: 用户发送的消息
 * - ASSISTANT: AI 助手的响应
 * - SYSTEM: 系统消息（如压缩摘要、提示等）
 * - ERROR: 错误消息
 */
enum class MessageRole {
    USER,      // 用户消息
    ASSISTANT, // AI 助手消息
    SYSTEM,    // 系统消息
    ERROR      // 错误消息
}

/**
 * 消息状态 - 跟踪消息的生命周期状态
 * 
 * 用于 UI 显示和状态管理。
 * 
 * 状态流转：
 * - SENDING -> STREAMING -> COMPLETE
 * - SENDING -> FAILED
 * - STREAMING -> FAILED
 * 
 * UI 响应：
 * - SENDING: 显示发送中指示器
 * - STREAMING: 显示流式动画（如光标闪烁）
 * - COMPLETE: 显示完整消息
 * - FAILED: 显示错误标记和重试选项
 */
enum class MessageStatus {
    SENDING,      // 正在发送
    STREAMING,    // 正在接收流式响应
    COMPLETE,     // 完成
    FAILED        // 失败
}

/**
 * 上下文显示类型 - 区分上下文的添加方式
 * 
 * 根据添加方式的不同，上下文在 UI 上的显示和处理方式也不同。
 * 
 * 类型区别：
 * - TAG: 通过 "Add Context" 按钮添加，显示为独立的上下文标签
 * - INLINE: 通过 @ 符号在消息中直接引用，作为消息内容的一部分
 * 
 * 处理区别：
 * - TAG 类型需要通过 MessageBuilderUtils 构建上下文块
 * - INLINE 类型直接嵌入在用户消息中
 */
enum class ContextDisplayType {
    TAG,     // 显示为标签（Add Context按钮添加）
    INLINE   // 内联显示（@符号触发添加）
}

/**
 * 内联文件引用 - 管理 @ 符号添加的文件引用
 * 
 * 当用户输入 @ 符号并选择文件时，创建此对象来管理引用信息。
 * 支持智能显示：在输入框中显示简短名称，发送时转换为完整路径。
 * 
 * 使用场景：
 * 1. 用户输入 @后选择文件
 * 2. 创建 InlineFileReference 实例
 * 3. 在输入框显示 @FileName.kt
 * 4. 发送时转换为 @src/path/to/FileName.kt
 */
data class InlineFileReference(
    val displayName: String,    // 显示名称：文件名，如 ContextSelectorTestApp.kt
    val fullPath: String,       // 完整路径：从项目根目录开始的完整路径
    val relativePath: String    // 相对路径：用于发送给 Claude 的路径
) {
    /**
     * 获取用于插入到文本中的显示文本
     * 
     * 返回格式：@FileName.kt
     * 这个文本会显示在输入框中，用户可见。
     * 
     * @return 带 @ 前缀的显示名称
     */
    fun getInlineText(): String = "@$displayName"
    
    /**
     * 获取发送时的完整路径文本
     * 
     * 返回格式：@src/path/to/FileName.kt
     * 发送消息时，会将显示文本替换为这个完整路径。
     * 
     * @return 带 @ 前缀的相对路径
     */
    fun getFullPathText(): String = "@$relativePath"
}

/**
 * 内联引用管理器 - 管理消息中的 @ 符号引用
 * 
 * 负责管理用户通过 @ 符号添加的所有文件引用。
 * 主要功能包括：
 * - 存储和管理引用映射
 * - 展开显示名称为完整路径
 * - 提取和验证引用
 * 
 * 工作流程：
 * 1. 用户输入 @ 并选择文件
 * 2. addReference() 添加引用到管理器
 * 3. 发送消息前调用 expandInlineReferences() 展开路径
 * 4. Claude 接收到完整路径后可以正确识别文件
 */
class InlineReferenceManager {
    private val referenceMap = mutableMapOf<String, InlineFileReference>()
    
    /**
     * 添加内联引用
     * 
     * 将文件引用添加到管理器中。
     * 使用显示文本（如 @FileName.kt）作为键。
     * 
     * @param reference 要添加的文件引用
     */
    fun addReference(reference: InlineFileReference) {
        referenceMap[reference.getInlineText()] = reference
    }
    
    /**
     * 移除内联引用
     */
    fun removeReference(inlineText: String) {
        referenceMap.remove(inlineText)
    }
    
    /**
     * 获取所有引用
     */
    fun getAllReferences(): Map<String, InlineFileReference> = referenceMap.toMap()
    
    /**
     * 清空所有引用
     */
    fun clear() {
        referenceMap.clear()
    }
    
    /**
     * 展开消息中的内联引用为完整路径
     * 
     * 在发送消息前调用，将所有 @FileName 格式的引用
     * 替换为 @path/to/FileName 格式的完整路径。
     * 
     * 示例：
     * 输入："请查看 @Main.kt 中的代码"
     * 输出："请查看 @src/main/kotlin/Main.kt 中的代码"
     * 
     * @param message 包含内联引用的原始消息
     * @return 展开后的消息
     */
    fun expandInlineReferences(message: String): String {
        val pattern = "@([\\w.-]+(?:\\.\\w+)?)".toRegex()
        return pattern.replace(message) { matchResult ->
            val inlineText = matchResult.value
            val reference = referenceMap[inlineText]
            reference?.getFullPathText() ?: inlineText
        }
    }
    
    /**
     * 从消息中提取所有 @ 符号引用
     * 
     * 使用正则表达式查找所有 @xxx 格式的引用。
     * 支持的格式：@FileName.ext、@file-name.ext 等。
     * 
     * @param message 要提取引用的消息
     * @return 所有找到的引用列表（包含 @ 符号）
     */
    fun extractInlineReferences(message: String): List<String> {
        val pattern = "@([\\w.-]+(?:\\.\\w+)?)".toRegex()
        return pattern.findAll(message).map { it.value }.toList()
    }
    
    /**
     * 检查消息中是否包含未知的内联引用
     * 
     * 用于验证消息中的所有 @ 引用是否都已注册。
     * 未知引用可能是用户手动输入的或已删除的文件。
     * 
     * @param message 要检查的消息
     * @return 未在管理器中注册的引用列表
     */
    fun hasUnknownReferences(message: String): List<String> {
        val extracted = extractInlineReferences(message)
        return extracted.filter { !referenceMap.containsKey(it) }
    }
}

/**
 * 上下文引用基类 - 定义所有可以作为上下文的引用类型
 * 
 * 使用密封类确保类型安全，所有子类必须在此文件中定义。
 * 每种引用类型代表一种特定的上下文信息。
 * 
 * 通用属性：
 * - displayType: 显示类型（TAG 或 INLINE）
 * - uri: 统一资源标识符，用于唯一标识资源
 * 
 * 扩展指南：
 * 添加新的上下文类型时，需要：
 * 1. 在此处添加新的 data class
 * 2. 在 MessageBuilderUtils 中添加显示逻辑
 * 3. 在相关 UI 组件中添加处理逻辑
 */
sealed class ContextReference {
    abstract val displayType: ContextDisplayType
    abstract val uri: String
    
    /**
     * 文件引用 - 引用单个文件
     * 
     * 最常用的上下文类型，用于引用项目中的源代码文件。
     * Claude 会读取文件内容作为上下文。
     * 
     * @param path 文件路径（可能是相对路径或绝对路径）
     * @param fullPath 完整路径（用于悬停提示）
     * @param displayType 显示类型
     */
    data class FileReference(
        val path: String,
        val fullPath: String = path,
        override val displayType: ContextDisplayType = ContextDisplayType.TAG
    ) : ContextReference() {
        override val uri: String
            get() = "file://$fullPath"
    }
    
    /**
     * Web 引用 - 引用网页内容
     * 
     * 用于引用在线文档、API 参考等网页内容。
     * Claude 会获取网页内容作为上下文。
     * 
     * @param url 完整 URL
     * @param title 网页标题（可选，用于悬停提示）
     * @param displayType 显示类型
     */
    data class WebReference(
        val url: String,
        val title: String? = null,
        override val displayType: ContextDisplayType = ContextDisplayType.TAG
    ) : ContextReference() {
        override val uri: String
            get() = url
    }
    
    /**
     * 文件夹引用 - 引用整个文件夹
     * 
     * 用于引用整个目录结构，如模块、包等。
     * Claude 会获取文件夹中的文件列表作为上下文。
     * 
     * @param path 文件夹路径
     * @param fileCount 文件数量
     * @param totalSize 总大小（字节）
     * @param displayType 显示类型
     * 
     * 注意：目前暂未启用，保留以便未来扩展
     */
    // 保留原有类型兼容性（暂时未使用）
    data class FolderReference(
        val path: String,
        val fileCount: Int = 0,
        val totalSize: Long = 0,
        override val displayType: ContextDisplayType = ContextDisplayType.TAG
    ) : ContextReference() {
        override val uri: String
            get() = "folder://$path"
    }
    
    /**
     * 符号引用 - 引用代码符号
     * 
     * 用于引用特定的代码符号，如类、函数、变量等。
     * 可以精确定位到代码的具体位置。
     * 
     * @param name 符号名称
     * @param type 符号类型
     * @param file 所在文件
     * @param line 所在行号
     * @param preview 代码预览（可选）
     * @param displayType 显示类型
     */
    data class SymbolReference(
        val name: String,
        val type: SymbolType,
        val file: String,
        val line: Int,
        val preview: String? = null,
        override val displayType: ContextDisplayType = ContextDisplayType.TAG
    ) : ContextReference() {
        override val uri: String
            get() = "symbol:$file#$name"
    }
    
    /**
     * 终端引用 - 引用终端输出
     * 
     * 用于引用命令行输出、日志等终端内容。
     * 帮助 Claude 理解执行结果和错误信息。
     * 
     * @param content 终端内容
     * @param lines 显示行数
     * @param timestamp 时间戳
     * @param isError 是否为错误输出
     * @param displayType 显示类型
     */
    data class TerminalReference(
        val content: String,
        val lines: Int = 50,
        val timestamp: Long = System.currentTimeMillis(),
        val isError: Boolean = false,
        override val displayType: ContextDisplayType = ContextDisplayType.TAG
    ) : ContextReference() {
        override val uri: String
            get() = "terminal:output?lines=$lines&ts=$timestamp"
    }
    
    /**
     * 问题引用 - 引用代码问题列表
     * 
     * 用于引用 IDE 检测到的问题，如编译错误、警告等。
     * 帮助 Claude 理解当前代码存在的问题。
     * 
     * @param problems 问题列表
     * @param severity 严重级别过滤（可选）
     * @param displayType 显示类型
     */
    data class ProblemsReference(
        val problems: List<Problem>,
        val severity: ProblemSeverity? = null,
        override val displayType: ContextDisplayType = ContextDisplayType.TAG
    ) : ContextReference() {
        override val uri: String
            get() = "problems:list?count=${problems.size}"
    }
    
    /**
     * Git 引用 - 引用 Git 信息
     * 
     * 用于引用 Git 状态、差异、历史等信息。
     * 帮助 Claude 理解代码变更和版本控制状态。
     * 
     * @param type Git 引用类型
     * @param content 内容
     * @param displayType 显示类型
     */
    data class GitReference(
        val type: GitRefType,
        val content: String,
        override val displayType: ContextDisplayType = ContextDisplayType.TAG
    ) : ContextReference() {
        override val uri: String
            get() = "git:${type.name.lowercase()}"
    }
    
    /**
     * 图片引用 - 引用图片文件
     * 
     * 用于引用截图、设计图、流程图等图片内容。
     * Claude 支持多模态输入，可以直接理解图片内容。
     * 
     * @param path 图片文件路径
     * @param filename 文件名
     * @param size 文件大小（字节）
     * @param mimeType MIME 类型（如 image/png）
     * @param displayType 显示类型
     */
    data class ImageReference(
        val path: String,
        val filename: String,
        val size: Long = 0,
        val mimeType: String = "image/*",
        override val displayType: ContextDisplayType = ContextDisplayType.TAG
    ) : ContextReference() {
        override val uri: String
            get() = "file://$path"
    }
    
    /**
     * 选中内容引用 - 引用当前选中的文本
     * 
     * 用于引用编辑器中当前选中的内容。
     * 方便快速引用正在查看的代码片段。
     * 
     * 注意：使用单例模式，因为只会有一个当前选中内容。
     */
    object SelectionReference : ContextReference() {
        override val displayType: ContextDisplayType = ContextDisplayType.TAG
        override val uri: String = "selection:current"
    }
    
    /**
     * 工作区引用 - 引用整个工作区
     * 
     * 用于引用整个项目工作区的结构信息。
     * 帮助 Claude 理解项目的整体结构和配置。
     * 
     * 注意：使用单例模式，因为只有一个工作区根目录。
     */
    object WorkspaceReference : ContextReference() {
        override val displayType: ContextDisplayType = ContextDisplayType.TAG
        override val uri: String = "workspace:root"
    }
}

/**
 * 符号类型 - 代码符号的分类
 * 
 * 定义代码中不同类型的符号，用于精确定位和引用。
 * 
 * 类型说明：
 * - CLASS: 类定义
 * - INTERFACE: 接口定义
 * - FUNCTION: 函数/方法
 * - PROPERTY: 属性（Kotlin）
 * - VARIABLE: 变量
 * - CONSTANT: 常量
 * - ENUM: 枚举
 * - OBJECT: 对象（Kotlin 单例）
 */
enum class SymbolType {
    CLASS,      // 类
    INTERFACE,  // 接口
    FUNCTION,   // 函数
    PROPERTY,   // 属性
    VARIABLE,   // 变量
    CONSTANT,   // 常量
    ENUM,       // 枚举
    OBJECT      // 对象
}

/**
 * 问题严重程度 - 代码问题的严重级别
 * 
 * 用于分类 IDE 检测到的各种问题。
 * 
 * 级别说明：
 * - ERROR: 错误 - 会导致编译失败或运行时异常
 * - WARNING: 警告 - 可能导致问题的代码
 * - INFO: 信息 - 代码改进建议
 * - HINT: 提示 - 轻微的代码优化建议
 */
enum class ProblemSeverity {
    ERROR,    // 错误
    WARNING,  // 警告
    INFO,     // 信息
    HINT      // 提示
}

/**
 * Git 引用类型 - Git 信息的分类
 * 
 * 定义可以引用的 Git 信息类型。
 * 
 * 类型说明：
 * - DIFF: 差异信息 - 未暂存的变更
 * - STAGED: 暂存区 - 已暂存但未提交的变更
 * - COMMITS: 提交历史 - 最近的提交记录
 * - BRANCHES: 分支信息 - 当前和远程分支
 * - STATUS: 状态信息 - 完整的 Git 状态
 */
enum class GitRefType {
    DIFF,     // 差异
    STAGED,   // 暂存区
    COMMITS,  // 提交历史
    BRANCHES, // 分支
    STATUS    // 状态
}

/**
 * 问题信息 - 代码问题的详细描述
 * 
 * 封装 IDE 检测到的单个问题信息。
 * 
 * @param severity 严重程度
 * @param message 问题描述
 * @param file 所在文件
 * @param line 所在行号
 * @param column 所在列号（可选）
 */
data class Problem(
    val severity: ProblemSeverity,
    val message: String,
    val file: String,
    val line: Int,
    val column: Int? = null
)

/**
 * 工具调用状态 - 跟踪工具执行的生命周期
 * 
 * 用于 UI 显示和状态管理。
 * 
 * 状态流转：
 * - PENDING -> RUNNING -> SUCCESS
 * - PENDING -> RUNNING -> FAILED
 * - PENDING -> CANCELLED
 * - RUNNING -> CANCELLED
 * 
 * UI 响应：
 * - PENDING: 显示等待图标
 * - RUNNING: 显示加载动画
 * - SUCCESS: 显示成功标记
 * - FAILED: 显示错误标记
 * - CANCELLED: 显示取消标记
 */
enum class ToolCallStatus {
    PENDING,   // 等待执行
    RUNNING,   // 正在执行
    SUCCESS,   // 执行成功
    FAILED,    // 执行失败
    CANCELLED  // 已取消
}

/**
 * 工具类型 - AI 可以调用的工具分类
 * 
 * 用于分类和统计 AI 使用的各种工具。
 * 不同类型的工具可能有不同的 UI 展示方式。
 * 
 * 工具说明：
 * - SEARCH_FILES: 搜索项目中的文件
 * - READ_FILE: 读取文件内容
 * - EDIT_FILE: 编辑文件内容
 * - RUN_COMMAND: 执行终端命令
 * - SEARCH_SYMBOLS: 搜索代码符号
 * - GET_PROBLEMS: 获取代码问题
 * - GIT_OPERATION: 执行 Git 操作
 * - WEB_SEARCH: 搜索网络内容
 * - OTHER: 其他未分类的工具
 */
enum class ToolType {
    SEARCH_FILES,     // 搜索文件
    READ_FILE,        // 读取文件
    EDIT_FILE,        // 编辑文件
    RUN_COMMAND,      // 运行命令
    SEARCH_SYMBOLS,   // 搜索符号
    GET_PROBLEMS,     // 获取问题
    GIT_OPERATION,    // Git 操作
    WEB_SEARCH,       // 网络搜索
    OTHER            // 其他工具
}

/**
 * 工具调用信息 - 封装单次工具调用的完整信息
 * 
 * 记录 AI 调用工具的所有相关信息，
 * 包括参数、状态、结果和时间信息。
 * 
 * @param id 唯一标识符
 * @param name 工具名称（Claude CLI 返回的原始名称）
 * @param tool 工具类型
 * @param displayName 显示名称（用于 UI）
 * @param parameters 工具参数
 * @param status 当前状态
 * @param result 执行结果
 * @param startTime 开始时间
 * @param endTime 结束时间
 */
data class ToolCall(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val tool: com.claudecodeplus.sdk.Tool? = null,  // 新的工具类型对象
    @Deprecated("Use tool property instead")
    val toolType: ToolType = ToolType.OTHER,  // 保留旧的枚举以保持兼容性
    val displayName: String = name,
    val parameters: Map<String, Any> = emptyMap(),
    val status: ToolCallStatus = ToolCallStatus.PENDING,
    val result: ToolResult? = null,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null
)

/**
 * 消息时间线元素 - 按时间顺序排列的消息组件
 * 
 * 用于保持消息内容和工具调用的时间顺序。
 * 这使得 UI 可以按照实际发生顺序显示各种元素。
 * 
 * 元素类型：
 * - ToolCallItem: 工具调用元素
 * - ContentItem: 文本内容元素
 * - StatusItem: 状态信息元素
 * 
 * 通用属性：
 * - timestamp: 时间戳，用于排序
 */
sealed class MessageTimelineItem {
    abstract val timestamp: Long
    
    /**
     * 工具调用元素
     * 
     * 表示消息中的一次工具调用。
     * 
     * @param toolCall 工具调用信息
     * @param timestamp 时间戳（默认使用工具调用的开始时间）
     */
    data class ToolCallItem(
        val toolCall: ToolCall,
        override val timestamp: Long = toolCall.startTime
    ) : MessageTimelineItem()
    
    /**
     * 文本内容元素
     * 
     * 表示消息中的文本内容段落。
     * 
     * @param content 文本内容
     * @param timestamp 时间戳
     */
    data class ContentItem(
        val content: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : MessageTimelineItem()
    
    /**
     * 状态元素
     * 
     * 表示消息中的状态信息，如"正在生成..."、"正在思考..."等。
     * 
     * @param status 状态文本
     * @param isStreaming 是否为流式状态
     * @param timestamp 时间戳
     */
    data class StatusItem(
        val status: String,
        val isStreaming: Boolean = false,
        override val timestamp: Long = System.currentTimeMillis()
    ) : MessageTimelineItem()
}

/**
 * 工具执行结果 - 封装工具执行的输出
 * 
 * 使用密封类区分成功和失败的结果。
 * 不同类型的结果有不同的 UI 展示方式。
 */
sealed class ToolResult {
    /**
     * 成功结果
     * 
     * @param output 完整输出
     * @param summary 摘要（用于折叠显示）
     * @param details 详细信息（可选）
     * @param affectedFiles 受影响的文件列表
     */
    data class Success(
        val output: String,
        val summary: String = output,
        val details: String? = null,
        val affectedFiles: List<String> = emptyList()
    ) : ToolResult()
    
    /**
     * 失败结果
     * 
     * @param error 错误信息
     * @param details 详细错误信息（可选）
     */
    data class Failure(
        val error: String,
        val details: String? = null
    ) : ToolResult()
    
    /**
     * 文件搜索结果
     * 
     * @param files 搜索到的文件列表
     * @param totalCount 总数量
     */
    data class FileSearchResult(
        val files: List<FileContext>,
        val totalCount: Int
    ) : ToolResult()
    
    /**
     * 文件读取结果
     * 
     * @param content 文件内容
     * @param lineCount 行数
     * @param language 编程语言（可选）
     */
    data class FileReadResult(
        val content: String,
        val lineCount: Int,
        val language: String? = null
    ) : ToolResult()
    
    /**
     * 文件编辑结果
     * 
     * @param oldContent 原始内容
     * @param newContent 新内容
     * @param changedLines 变更的行范围
     */
    data class FileEditResult(
        val oldContent: String,
        val newContent: String,
        val changedLines: IntRange
    ) : ToolResult()
    
    /**
     * 命令执行结果
     * 
     * @param output 命令输出
     * @param exitCode 退出码
     * @param duration 执行时长（毫秒）
     */
    data class CommandResult(
        val output: String,
        val exitCode: Int,
        val duration: Long
    ) : ToolResult()
}

/**
 * 增强的消息模型 - 核心消息数据结构
 * 
 * 这是整个聊天系统的核心数据结构，封装了一条完整消息的所有信息。
 * 支持流式更新、工具调用、上下文引用等高级特性。
 * 
 * @param id 消息唯一标识符
 * @param role 消息角色
 * @param content 消息内容
 * @param timestamp 时间戳
 * @param contexts 上下文引用列表
 * @param toolCalls 工具调用列表
 * @param model 使用的 AI 模型
 * @param status 消息状态
 * @param isStreaming 是否正在流式传输
 * @param isError 是否为错误消息
 * @param orderedElements 按时间顺序排列的元素
 * @param tokenUsage Token 使用统计
 * @param isCompactSummary 是否为压缩摘要
 */
data class
EnhancedMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val contexts: List<ContextReference> = emptyList(),      // 上下文引用
    val toolCalls: List<ToolCall> = emptyList(),            // 工具调用
    val model: AiModel? = null,                              // 使用的模型
    val status: MessageStatus = MessageStatus.COMPLETE,       // 消息状态
    val isStreaming: Boolean = false,                        // 流式传输状态
    val isError: Boolean = false,                            // 错误标记
    val orderedElements: List<MessageTimelineItem> = emptyList(), // 有序元素
    val tokenUsage: TokenUsage? = null,                      // Token 使用情况
    val isCompactSummary: Boolean = false                    // 压缩摘要标记
) {
    /**
     * 向后兼容属性
     * 保留旧版本中使用的 modelName 属性
     */
    val modelName: String? get() = model?.cliName
    
    /**
     * Token 使用信息 - 统计 API 调用的 Token 消耗
     * 
     * 用于跟踪和显示每次对话的 Token 使用情况。
     * 
     * @param inputTokens 输入 Token 数
     * @param outputTokens 输出 Token 数
     * @param cacheCreationTokens 缓存创建 Token 数
     * @param cacheReadTokens 缓存读取 Token 数
     */
    data class TokenUsage(
        val inputTokens: Int,
        val outputTokens: Int,
        val cacheCreationTokens: Int = 0,
        val cacheReadTokens: Int = 0
    ) {
        val totalTokens: Int get() = inputTokens + outputTokens
    }
}

/**
 * 文件上下文 - 描述文件的详细信息
 * 
 * 用于文件搜索结果和文件列表显示。
 * 
 * @param path 文件路径
 * @param name 文件名
 * @param extension 文件扩展名
 * @param size 文件大小（字节）
 * @param lastModified 最后修改时间
 * @param preview 内容预览（可选）
 */
data class FileContext(
    val path: String,
    val name: String,
    val extension: String,
    val size: Long,
    val lastModified: Long,
    val preview: String? = null
)

/**
 * 符号上下文 - 描述代码符号的详细信息
 * 
 * 用于符号搜索和导航。
 * 
 * @param name 符号名称
 * @param type 符号类型
 * @param file 所在文件
 * @param line 所在行号
 * @param signature 函数签名（可选）
 * @param documentation 文档注释（可选）
 */
data class SymbolContext(
    val name: String,
    val type: SymbolType,
    val file: String,
    val line: Int,
    val signature: String? = null,
    val documentation: String? = null
)

/**
 * 终端上下文 - 描述终端输出信息
 * 
 * 用于封装命令执行的输出结果。
 * 
 * @param output 输出内容
 * @param timestamp 时间戳
 * @param hasErrors 是否包含错误
 * @param command 执行的命令（可选）
 */
data class TerminalContext(
    val output: String,
    val timestamp: Long,
    val hasErrors: Boolean,
    val command: String? = null
)

/**
 * Git 上下文 - 描述 Git 相关信息
 * 
 * 用于封装各种 Git 操作的结果。
 * 
 * @param type Git 信息类型
 * @param content 内容
 * @param files 受影响的文件列表
 * @param stats 统计信息（可选）
 */
data class GitContext(
    val type: GitRefType,
    val content: String,
    val files: List<String> = emptyList(),
    val stats: GitStats? = null
)

/**
 * Git 统计信息 - Git 变更的统计数据
 * 
 * @param additions 新增行数
 * @param deletions 删除行数
 * @param filesChanged 变更文件数
 */
data class GitStats(
    val additions: Int,
    val deletions: Int,
    val filesChanged: Int
)

/**
 * 文件夹上下文 - 描述文件夹的详细信息
 * 
 * 用于文件夹浏览和统计。
 * 
 * @param path 文件夹路径
 * @param fileCount 文件数量
 * @param folderCount 子文件夹数量
 * @param totalSize 总大小（字节）
 * @param files 文件列表
 */
data class FolderContext(
    val path: String,
    val fileCount: Int,
    val folderCount: Int,
    val totalSize: Long,
    val files: List<FileContext>
)