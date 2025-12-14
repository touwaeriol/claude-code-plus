package com.asakii.plugin.mcp

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import com.networknt.schema.ValidationMessage
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * 参数校验错误
 */
data class ValidationError(
    val parameter: String,
    val message: String,
    val hint: String? = null
)

/**
 * 校验结果
 */
sealed class ValidationResult {
    object Valid : ValidationResult()
    data class Invalid(val errors: List<ValidationError>) : ValidationResult() {
        /**
         * 格式化错误信息，适合返回给用户
         */
        fun formatMessage(): String {
            val sb = StringBuilder()
            sb.appendLine("Parameter validation failed (${errors.size} error${if (errors.size > 1) "s" else ""}):")
            sb.appendLine()

            errors.forEachIndexed { index, error ->
                sb.appendLine("${index + 1}. [${error.parameter}] ${error.message}")
                error.hint?.let { hint ->
                    hint.lines().forEach { line ->
                        sb.appendLine("   $line")
                    }
                }
                if (index < errors.size - 1) sb.appendLine()
            }

            return sb.toString().trimEnd()
        }
    }
}

/**
 * JSON Schema 参数校验器
 *
 * 使用 networknt/json-schema-validator 库进行校验。
 * 基于 tools.json 中定义的 JSON Schema 校验工具参数。
 *
 * 支持以下校验：
 * - required: 必填参数
 * - type: 参数类型 (string, integer, number, boolean, array, object)
 * - enum: 枚举值
 * - minimum/maximum: 数值范围
 * - minLength/maxLength: 字符串长度
 * - items.enum: 数组元素枚举值
 *
 * 使用示例：
 * ```kotlin
 * val result = SchemaValidator.validate("FindUsages", arguments)
 * if (result is ValidationResult.Invalid) {
 *     return ToolResult.error(result.formatMessage())
 * }
 * ```
 */
object SchemaValidator {

    private val objectMapper = ObjectMapper()
    private val schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7)

    /**
     * 校验参数
     *
     * @param toolName 工具名称，用于获取对应的 Schema
     * @param arguments 要校验的参数
     * @param customValidators 自定义校验器，用于 Schema 无法表达的复杂逻辑
     * @return 校验结果
     */
    fun validate(
        toolName: String,
        arguments: Map<String, Any>,
        customValidators: List<(Map<String, Any>) -> ValidationError?> = emptyList()
    ): ValidationResult {
        val schema = ToolSchemaLoader.getSchema(toolName)
        if (schema.isEmpty()) {
            logger.warn { "No schema found for tool: $toolName, skipping validation" }
            return ValidationResult.Valid
        }

        val errors = mutableListOf<ValidationError>()

        // 1. 使用 JSON Schema 库校验
        try {
            val schemaNode = objectMapper.valueToTree<JsonNode>(schema)
            val jsonSchema = schemaFactory.getSchema(schemaNode)
            val dataNode = objectMapper.valueToTree<JsonNode>(arguments)

            val validationMessages = jsonSchema.validate(dataNode)

            for (msg in validationMessages) {
                errors.add(convertToValidationError(msg, schema))
            }
        } catch (e: Exception) {
            logger.error(e) { "JSON Schema validation failed for tool: $toolName" }
            // 如果库校验失败，回退到基础校验
            errors.addAll(fallbackValidation(schema, arguments))
        }

        // 2. 运行自定义校验器
        for (validator in customValidators) {
            validator(arguments)?.let { errors.add(it) }
        }

        return if (errors.isEmpty()) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(errors)
        }
    }

    /**
     * 将 JSON Schema 库的 ValidationMessage 转换为我们的 ValidationError
     */
    private fun convertToValidationError(
        msg: ValidationMessage,
        schema: Map<String, Any>
    ): ValidationError {
        // 从 instanceLocation 提取参数名
        // 例如: "$.symbolName" -> "symbolName", "$" -> "(root)"
        val path = msg.instanceLocation?.toString() ?: msg.evaluationPath?.toString() ?: ""
        val paramName = when {
            path == "$" || path.isEmpty() -> extractParamFromMessage(msg.message)
            path.startsWith("$.") -> path.removePrefix("$.")
            path.startsWith("/") -> path.removePrefix("/").replace("/", ".")
            else -> path
        }

        // 生成人类可读的错误信息
        val message = formatErrorMessage(msg, paramName)

        // 生成提示信息
        val hint = generateHint(paramName, msg.type, schema)

        return ValidationError(
            parameter = paramName.ifEmpty { "(unknown)" },
            message = message,
            hint = hint
        )
    }

    /**
     * 从错误消息中提取参数名
     */
    private fun extractParamFromMessage(message: String): String {
        // 尝试从消息中提取参数名，例如 "required property 'symbolName' not found"
        val regex = Regex("""'([^']+)'""")
        return regex.find(message)?.groupValues?.get(1) ?: "(root)"
    }

    /**
     * 格式化错误信息为人类可读的形式
     */
    private fun formatErrorMessage(msg: ValidationMessage, paramName: String): String {
        val originalMessage = msg.message ?: "Validation failed"
        val type = msg.type ?: ""

        return when {
            type.contains("required") -> "Missing required parameter"
            type.contains("type") -> {
                // 提取期望的类型
                val expectedType = Regex("""expected type: (\w+)""").find(originalMessage)?.groupValues?.get(1)
                    ?: Regex("""type: (\w+)""").find(originalMessage)?.groupValues?.get(1)
                if (expectedType != null) {
                    "Invalid type: expected $expectedType"
                } else {
                    "Invalid type"
                }
            }
            type.contains("enum") -> {
                "Invalid value"
            }
            type.contains("minimum") -> {
                val minimum = Regex("""minimum (-?\d+\.?\d*)""").find(originalMessage)?.groupValues?.get(1)
                "Value is below minimum${minimum?.let { " ($it)" } ?: ""}"
            }
            type.contains("maximum") -> {
                val maximum = Regex("""maximum (-?\d+\.?\d*)""").find(originalMessage)?.groupValues?.get(1)
                "Value exceeds maximum${maximum?.let { " ($it)" } ?: ""}"
            }
            type.contains("minLength") -> "String is too short"
            type.contains("maxLength") -> "String is too long"
            type.contains("pattern") -> "Value does not match the required pattern"
            else -> originalMessage
        }
    }

    /**
     * 生成提示信息
     */
    private fun generateHint(paramName: String, errorType: String?, schema: Map<String, Any>): String? {
        @Suppress("UNCHECKED_CAST")
        val properties = schema["properties"] as? Map<String, Map<String, Any>> ?: return null
        val paramSchema = properties[paramName] ?: return null

        return when {
            errorType?.contains("enum") == true -> {
                @Suppress("UNCHECKED_CAST")
                val enumValues = paramSchema["enum"] as? List<Any>
                enumValues?.let { "Valid values: ${it.joinToString(", ")}" }
            }
            errorType?.contains("required") == true -> {
                val description = paramSchema["description"] as? String
                description?.let { "Description: $it" }
            }
            errorType?.contains("type") == true -> {
                val expectedType = paramSchema["type"] as? String
                getTypeHint(expectedType)
            }
            errorType?.contains("minimum") == true || errorType?.contains("maximum") == true -> {
                val min = paramSchema["minimum"]
                val max = paramSchema["maximum"]
                when {
                    min != null && max != null -> "Valid range: $min to $max"
                    min != null -> "Minimum value: $min"
                    max != null -> "Maximum value: $max"
                    else -> null
                }
            }
            else -> {
                val description = paramSchema["description"] as? String
                description?.let { "Description: $it" }
            }
        }
    }

    private fun getTypeHint(type: String?): String? {
        return when (type) {
            "string" -> "Expected a text string"
            "integer" -> "Expected a whole number (e.g., 1, 42, 100)"
            "number" -> "Expected a number (e.g., 1, 3.14, -5)"
            "boolean" -> "Expected true or false"
            "array" -> "Expected an array/list (e.g., [\"a\", \"b\"])"
            "object" -> "Expected an object (e.g., {\"key\": \"value\"})"
            else -> null
        }
    }

    /**
     * 回退校验：当 JSON Schema 库校验失败时使用
     */
    private fun fallbackValidation(
        schema: Map<String, Any>,
        arguments: Map<String, Any>
    ): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        @Suppress("UNCHECKED_CAST")
        val properties = schema["properties"] as? Map<String, Map<String, Any>> ?: return errors
        @Suppress("UNCHECKED_CAST")
        val required = schema["required"] as? List<String> ?: emptyList()

        // 校验必填参数
        for (paramName in required) {
            val value = arguments[paramName]
            if (value == null) {
                val paramSchema = properties[paramName] ?: emptyMap()
                val description = paramSchema["description"] as? String
                errors.add(ValidationError(
                    parameter = paramName,
                    message = "Missing required parameter",
                    hint = description?.let { "Description: $it" }
                ))
            } else if (value is String && value.isBlank()) {
                errors.add(ValidationError(
                    parameter = paramName,
                    message = "Required parameter cannot be empty"
                ))
            }
        }

        // 校验枚举值
        for ((paramName, value) in arguments) {
            val paramSchema = properties[paramName] ?: continue
            @Suppress("UNCHECKED_CAST")
            val enumValues = paramSchema["enum"] as? List<Any>
            if (enumValues != null && value !in enumValues) {
                errors.add(ValidationError(
                    parameter = paramName,
                    message = "Invalid value: '$value'",
                    hint = "Valid values: ${enumValues.joinToString(", ")}"
                ))
            }
        }

        return errors
    }

    /**
     * 便捷方法：创建自定义校验器，用于"至少需要其中一个参数"的场景
     */
    fun requireAtLeastOne(vararg params: String, message: String? = null): (Map<String, Any>) -> ValidationError? {
        return { arguments ->
            val hasAny = params.any { param ->
                val value = arguments[param]
                value != null && (value !is String || value.isNotBlank())
            }
            if (!hasAny) {
                ValidationError(
                    parameter = params.joinToString("/"),
                    message = message ?: "At least one of these parameters is required: ${params.joinToString(", ")}",
                    hint = "Provide at least one: ${params.joinToString(" or ")}"
                )
            } else null
        }
    }

    /**
     * 便捷方法：创建自定义校验器，用于"参数 A 依赖参数 B"的场景
     */
    fun requireIfPresent(trigger: String, triggerValues: List<Any>, required: String): (Map<String, Any>) -> ValidationError? {
        return { arguments ->
            val triggerValue = arguments[trigger]
            if (triggerValue != null && triggerValue in triggerValues) {
                val requiredValue = arguments[required]
                if (requiredValue == null || (requiredValue is String && requiredValue.isBlank())) {
                    ValidationError(
                        parameter = required,
                        message = "Required when $trigger is ${triggerValues.joinToString(" or ") { "'$it'" }}",
                        hint = "Please provide '$required' parameter"
                    )
                } else null
            } else null
        }
    }
}
