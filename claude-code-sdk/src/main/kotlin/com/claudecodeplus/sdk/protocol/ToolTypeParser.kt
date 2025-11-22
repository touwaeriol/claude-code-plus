package com.claudecodeplus.sdk.protocol

import com.claudecodeplus.sdk.exceptions.MessageParsingException
import com.claudecodeplus.sdk.types.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.json.*

/**
 * 工具类型解析器
 *
 * 负责将通用的ToolUseBlock转换为具体的工具类型。
 * 这个解析器根据工具名称和参数创建相应的具体工具实例。
 */
object ToolTypeParser {

    /**
     * 将ToolUseBlock解析为具体的工具类型
     *
     * @param block 通用的工具使用块
     * @return 具体的工具类型实例
     */
    fun parseToolUseBlock(block: ToolUseBlock): SpecificToolUse {
        return try {
            when (block.name) {
                "Bash" -> parseBashTool(block)
                "BashOutput" -> parseBashOutputTool(block)
                "KillShell" -> parseKillShellTool(block)
                "Edit" -> parseEditTool(block)
                "MultiEdit" -> parseMultiEditTool(block)
                "Read" -> parseReadTool(block)
                "Write" -> parseWriteTool(block)
                "Glob" -> parseGlobTool(block)
                "Grep" -> parseGrepTool(block)
                "WebFetch" -> parseWebFetchTool(block)
                "WebSearch" -> parseWebSearchTool(block)
                "TodoWrite" -> parseTodoWriteTool(block)
                "Task" -> parseTaskTool(block)
                "NotebookEdit" -> parseNotebookEditTool(block)
                "ExitPlanMode" -> parseExitPlanModeTool(block)
                "ListMcpResourcesTool" -> parseListMcpResourcesTool(block)
                "ReadMcpResourceTool" -> parseReadMcpResourceTool(block)
                else -> {
                    // 检查是否是MCP工具
                    if (block.name.startsWith("mcp__")) {
                        parseMcpTool(block)
                    } else {
                        parseUnknownTool(block)
                    }
                }
            }
        } catch (e: Exception) {
            // 解析失败时回退到未知工具类型
            println("[ToolTypeParser] ⚠️ 解析工具失败 ${block.name}: ${e.message}")
            parseUnknownTool(block)
        }
    }

    /**
     * 解析Bash工具
     */
    private fun parseBashTool(block: ToolUseBlock): BashToolUse {
        val params = block.input.jsonObject
        return BashToolUse(
            id = block.id,
            name = block.name,
            input = block.input,
            command = params["command"]?.jsonPrimitive?.content
                ?: throw MessageParsingException("Missing 'command' in Bash tool"),
            description = params["description"]?.jsonPrimitive?.contentOrNull,
            timeout = params["timeout"]?.jsonPrimitive?.longOrNull,
            runInBackground = params["run_in_background"]?.jsonPrimitive?.booleanOrNull ?: false
        )
    }

    /**
     * 解析Edit工具
     */
    private fun parseEditTool(block: ToolUseBlock): EditToolUse {
        val params = block.input.jsonObject
        return EditToolUse(
            id = block.id,
            name = block.name,
            input = block.input,
            filePath = params["file_path"]?.jsonPrimitive?.content
                ?: throw MessageParsingException("Missing 'file_path' in Edit tool"),
            oldString = params["old_string"]?.jsonPrimitive?.content
                ?: throw MessageParsingException("Missing 'old_string' in Edit tool"),
            newString = params["new_string"]?.jsonPrimitive?.content
                ?: throw MessageParsingException("Missing 'new_string' in Edit tool"),
            replaceAll = params["replace_all"]?.jsonPrimitive?.booleanOrNull ?: false
        )
    }

    /**
     * 解析MultiEdit工具
     */
    private fun parseMultiEditTool(block: ToolUseBlock): MultiEditToolUse {
        val params = block.input.jsonObject
        val editsArray = params["edits"]?.jsonArray
            ?: throw MessageParsingException("Missing 'edits' in MultiEdit tool")

        val edits = editsArray.map { editElement ->
            val editObj = editElement.jsonObject
            MultiEditToolUse.EditOperation(
                oldString = editObj["old_string"]?.jsonPrimitive?.content
                    ?: throw MessageParsingException("Missing 'old_string' in edit operation"),
                newString = editObj["new_string"]?.jsonPrimitive?.content
                    ?: throw MessageParsingException("Missing 'new_string' in edit operation"),
                replaceAll = editObj["replace_all"]?.jsonPrimitive?.booleanOrNull ?: false
            )
        }

        return MultiEditToolUse(
            id = block.id,
            name = block.name,
            input = block.input,
            filePath = params["file_path"]?.jsonPrimitive?.content
                ?: throw MessageParsingException("Missing 'file_path' in MultiEdit tool"),
            edits = edits
        )
    }

    /**
     * 解析Read工具
     */
    private fun parseReadTool(block: ToolUseBlock): ReadToolUse {
        val params = block.input.jsonObject
        return ReadToolUse(
            id = block.id,
            name = block.name,
            input = block.input,
            filePath = params["file_path"]?.jsonPrimitive?.content
                ?: throw MessageParsingException("Missing 'file_path' in Read tool"),
            offset = params["offset"]?.jsonPrimitive?.intOrNull,
            limit = params["limit"]?.jsonPrimitive?.intOrNull
        )
    }

    /**
     * 解析Write工具
     */
    private fun parseWriteTool(block: ToolUseBlock): WriteToolUse {
        val params = block.input.jsonObject
        return WriteToolUse(
            id = block.id,
            name = block.name,
            input = block.input,
            filePath = params["file_path"]?.jsonPrimitive?.content
                ?: throw MessageParsingException("Missing 'file_path' in Write tool"),
            content = params["content"]?.jsonPrimitive?.content
                ?: throw MessageParsingException("Missing 'content' in Write tool")
        )
    }

    /**
     * 解析Glob工具
     */
    private fun parseGlobTool(block: ToolUseBlock): GlobToolUse {
        val params = block.input.jsonObject
        return GlobToolUse(
            id = block.id,
            name = block.name,
            input = block.input,
            pattern = params["pattern"]?.jsonPrimitive?.content
                ?: throw MessageParsingException("Missing 'pattern' in Glob tool"),
            path = params["path"]?.jsonPrimitive?.contentOrNull
        )
    }

    /**
     * 解析Grep工具
     */
    private fun parseGrepTool(block: ToolUseBlock): GrepToolUse {
        val params = block.input.jsonObject
        return GrepToolUse(
            id = block.id,
            name = block.name,
            input = block.input,
            pattern = params["pattern"]?.jsonPrimitive?.content
                ?: throw MessageParsingException("Missing 'pattern' in Grep tool"),
            path = params["path"]?.jsonPrimitive?.contentOrNull,
            outputMode = params["output_mode"]?.jsonPrimitive?.contentOrNull,
            glob = params["glob"]?.jsonPrimitive?.contentOrNull,
            type = params["type"]?.jsonPrimitive?.contentOrNull,
            caseInsensitive = params["-i"]?.jsonPrimitive?.booleanOrNull ?: false,
            showLineNumbers = params["-n"]?.jsonPrimitive?.booleanOrNull ?: false,
            contextBefore = params["-B"]?.jsonPrimitive?.intOrNull,
            contextAfter = params["-A"]?.jsonPrimitive?.intOrNull,
            headLimit = params["head_limit"]?.jsonPrimitive?.intOrNull
        )
    }

    /**
     * 解析WebFetch工具
     */
    private fun parseWebFetchTool(block: ToolUseBlock): WebFetchToolUse {
        val params = block.input.jsonObject
        return WebFetchToolUse(
            id = block.id,
            name = block.name,
            input = block.input,
            url = params["url"]?.jsonPrimitive?.content
                ?: throw MessageParsingException("Missing 'url' in WebFetch tool"),
            prompt = params["prompt"]?.jsonPrimitive?.content
                ?: throw MessageParsingException("Missing 'prompt' in WebFetch tool")
        )
    }

    /**
     * 解析WebSearch工具
     */
    private fun parseWebSearchTool(block: ToolUseBlock): WebSearchToolUse {
        val params = block.input.jsonObject
        return WebSearchToolUse(
            id = block.id,
            name = block.name,
            input = block.input,
            query = params["query"]?.jsonPrimitive?.content
                ?: throw MessageParsingException("Missing 'query' in WebSearch tool"),
            allowedDomains = params["allowed_domains"]?.jsonArray?.map { it.jsonPrimitive.content },
            blockedDomains = params["blocked_domains"]?.jsonArray?.map { it.jsonPrimitive.content }
        )
    }

    /**
     * 解析TodoWrite工具
     */
    private fun parseTodoWriteTool(block: ToolUseBlock): TodoWriteToolUse {
        val params = block.input.jsonObject
        val todosArray = params["todos"]?.jsonArray
            ?: throw MessageParsingException("Missing 'todos' in TodoWrite tool")

        val todos = todosArray.map { todoElement ->
            val todoObj = todoElement.jsonObject
            TodoWriteToolUse.TodoItem(
                content = todoObj["content"]?.jsonPrimitive?.content
                    ?: throw MessageParsingException("Missing 'content' in todo item"),
                status = todoObj["status"]?.jsonPrimitive?.content
                    ?: throw MessageParsingException("Missing 'status' in todo item"),
                activeForm = todoObj["activeForm"]?.jsonPrimitive?.content
                    ?: throw MessageParsingException("Missing 'activeForm' in todo item")
            )
        }

        return TodoWriteToolUse(
            id = block.id,
            name = block.name,
            input = block.input,
            todos = todos
        )
    }

    /**
     * 解析Task工具
     */
    private fun parseTaskTool(block: ToolUseBlock): TaskToolUse {
        val params = block.input.jsonObject
        return TaskToolUse(
            id = block.id,
            name = block.name,
            input = block.input,
            description = params["description"]?.jsonPrimitive?.content
                ?: throw MessageParsingException("Missing 'description' in Task tool"),
            prompt = params["prompt"]?.jsonPrimitive?.content
                ?: throw MessageParsingException("Missing 'prompt' in Task tool"),
            subagentType = params["subagent_type"]?.jsonPrimitive?.content
                ?: throw MessageParsingException("Missing 'subagent_type' in Task tool")
        )
    }

    /**
     * 解析NotebookEdit工具
     */
    private fun parseNotebookEditTool(block: ToolUseBlock): NotebookEditToolUse {
        val params = block.input.jsonObject
        return NotebookEditToolUse(
            id = block.id,
            name = block.name,
            input = block.input,
            notebookPath = params["notebook_path"]?.jsonPrimitive?.content
                ?: throw MessageParsingException("Missing 'notebook_path' in NotebookEdit tool"),
            newSource = params["new_source"]?.jsonPrimitive?.content
                ?: throw MessageParsingException("Missing 'new_source' in NotebookEdit tool"),
            cellId = params["cell_id"]?.jsonPrimitive?.contentOrNull,
            cellType = params["cell_type"]?.jsonPrimitive?.contentOrNull,
            editMode = params["edit_mode"]?.jsonPrimitive?.contentOrNull
        )
    }

    /**
     * 解析MCP工具
     */
    private fun parseMcpTool(block: ToolUseBlock): McpToolUse {
        // MCP工具名称格式: mcp__server_name__function_name
        val nameParts = block.name.split("__")
        val serverName = if (nameParts.size >= 2) nameParts[1] else "unknown"
        val functionName = if (nameParts.size >= 3) nameParts[2] else block.name

        // 将JsonElement转换为Map<String, Any>
        val parameters = parseJsonElementToMap(block.input)

        return McpToolUse(
            id = block.id,
            name = block.name,
            input = block.input,
            fullToolName = block.name,
            serverName = serverName,
            functionName = functionName,
            parameters = parameters
        )
    }

    /**
     * 解析BashOutput工具
     */
    private fun parseBashOutputTool(block: ToolUseBlock): BashOutputToolUse {
        val params = block.input.jsonObject
        return BashOutputToolUse(
            id = block.id,
            name = block.name,
            input = block.input,
            bashId = params["bash_id"]?.jsonPrimitive?.content
                ?: throw MessageParsingException("Missing 'bash_id' in BashOutput tool"),
            filter = params["filter"]?.jsonPrimitive?.contentOrNull
        )
    }

    /**
     * 解析KillShell工具
     */
    private fun parseKillShellTool(block: ToolUseBlock): KillShellToolUse {
        val params = block.input.jsonObject
        return KillShellToolUse(
            id = block.id,
            name = block.name,
            input = block.input,
            shellId = params["shell_id"]?.jsonPrimitive?.content
                ?: throw MessageParsingException("Missing 'shell_id' in KillShell tool")
        )
    }

    /**
     * 解析ExitPlanMode工具
     */
    private fun parseExitPlanModeTool(block: ToolUseBlock): ExitPlanModeToolUse {
        val params = block.input.jsonObject
        return ExitPlanModeToolUse(
            id = block.id,
            name = block.name,
            input = block.input,
            plan = params["plan"]?.jsonPrimitive?.content
                ?: throw MessageParsingException("Missing 'plan' in ExitPlanMode tool")
        )
    }

    /**
     * 解析ListMcpResources工具
     */
    private fun parseListMcpResourcesTool(block: ToolUseBlock): ListMcpResourcesToolUse {
        val params = block.input.jsonObject
        return ListMcpResourcesToolUse(
            id = block.id,
            name = block.name,
            input = block.input,
            server = params["server"]?.jsonPrimitive?.contentOrNull
        )
    }

    /**
     * 解析ReadMcpResource工具
     */
    private fun parseReadMcpResourceTool(block: ToolUseBlock): ReadMcpResourceToolUse {
        val params = block.input.jsonObject
        return ReadMcpResourceToolUse(
            id = block.id,
            name = block.name,
            input = block.input,
            server = params["server"]?.jsonPrimitive?.content
                ?: throw MessageParsingException("Missing 'server' in ReadMcpResource tool"),
            uri = params["uri"]?.jsonPrimitive?.content
                ?: throw MessageParsingException("Missing 'uri' in ReadMcpResource tool")
        )
    }

    /**
     * 解析未知工具
     */
    private fun parseUnknownTool(block: ToolUseBlock): UnknownToolUse {
        val parameters = parseJsonElementToMap(block.input)
        return UnknownToolUse(
            id = block.id,
            name = block.name,
            input = block.input,
            toolName = block.name,
            parameters = parameters
        )
    }

    /**
     * 将JsonElement转换为Map<String, Any>
     * 这是一个递归函数，用于处理嵌套的JSON结构
     */
    private fun parseJsonElementToMap(element: JsonElement): Map<String, @Contextual Any> {
        return when (element) {
            is JsonObject -> {
                element.mapValues { (_, value) ->
                    parseJsonValue(value)
                }
            }
            else -> mapOf("value" to parseJsonValue(element))
        }
    }

    /**
     * 递归解析JsonElement为Kotlin类型
     */
    private fun parseJsonValue(element: JsonElement): Any {
        return when (element) {
            is JsonPrimitive -> {
                when {
                    element.isString -> element.content
                    element.booleanOrNull != null -> element.boolean
                    element.intOrNull != null -> element.int
                    element.longOrNull != null -> element.long
                    element.doubleOrNull != null -> element.double
                    else -> element.content
                }
            }
            is JsonArray -> {
                element.map { parseJsonValue(it) }
            }
            is JsonObject -> {
                element.mapValues { (_, value) -> parseJsonValue(value) }
            }
            else -> element.toString()
        }
    }
}