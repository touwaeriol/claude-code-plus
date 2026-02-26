package com.asakii.settings

import kotlinx.serialization.Serializable

/**
 * 默认思考等级枚举
 *
 * 简化为三个核心级别：Off、Think、Ultra
 */
enum class DefaultThinkingLevel(val displayName: String, val description: String) {
    OFF("Off", "Disable extended thinking"),
    THINK("Think", "Standard thinking for most tasks"),
    ULTRA("Ultra", "Deep thinking for complex tasks");

    companion object {
        fun fromName(name: String?): DefaultThinkingLevel? {
            return entries.find { it.name == name }
        }
    }
}

/**
 * 思考级别配置
 *
 * 用于存储思考级别的完整信息，包括预设级别和自定义级别
 */
@Serializable
data class ThinkingLevelConfig(
    val id: String,        // 唯一标识：off, think, ultra, custom_xxx
    val name: String,      // 显示名称
    val tokens: Int,       // token 数量
    val isCustom: Boolean = false  // 是否为自定义级别
)

/**
 * 通用选项配置
 *
 * 用于下拉列表选项，支持动态返回给前端
 */
@Serializable
data class OptionConfig(
    val id: String,           // 唯一标识
    val label: String,        // 显示名称
    val description: String = "",  // 描述（可选）
    val isDefault: Boolean = false // 是否为默认值
)

/**
 * 自定义模型配置
 *
 * 用于存储用户自定义的模型信息
 */
@Serializable
data class CustomModelConfig(
    val id: String,        // 唯一标识（如 "custom_xxx"）
    val displayName: String,  // 显示名称（如 "My Custom Model"）
    val modelId: String       // 模型 ID（如 "claude-sonnet-4-6"）
)

/**
 * 外部路径规则类型
 */
enum class ExternalPathRuleType {
    INCLUDE,  // 包含（允许访问）
    EXCLUDE   // 排除（禁止访问）
}

/**
 * 外部路径访问规则
 *
 * 用于控制哪些项目外部路径可以被访问
 */
@Serializable
data class ExternalPathRule(
    val path: String,                    // 目录路径
    val type: ExternalPathRuleType       // 规则类型
)

/**
 * 统一的模型信息类
 *
 * 用于统一表示内置模型和自定义模型
 */
data class ModelInfo(
    val modelId: String,      // 实际模型 ID
    val displayName: String,  // 显示名称
    val isBuiltIn: Boolean    // 是否为内置模型
)
