package com.asakii.claude.agent.sdk.mcp.annotations

import kotlin.reflect.KClass

/**
 * MCP工具方法注解
 * 
 * 用于标记一个方法为MCP工具，自动注册为可调用的工具。
 * 
 * 使用示例：
 * ```kotlin
 * @McpTool("计算两个数的和")
 * suspend fun add(a: Double, b: Double): Double {
 *     return a + b
 * }
 * ```
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class McpTool(
    /**
     * 工具描述，用于帮助AI理解工具用途
     */
    val value: String
)

/**
 * 工具参数注解
 * 
 * 用于描述方法参数，AI 通过自然语言理解所有约束和要求。
 * 
 * 使用示例：
 * ```kotlin
 * suspend fun divide(
 *     @ToolParam("被除数，必须是正数") dividend: Double,
 *     @ToolParam("除数，不能为0") divisor: Double,
 *     @ToolParam("保留小数位数，可选，默认2位，范围0-10") precision: Int = 2
 * ): Double
 * ```
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class ToolParam(
    /**
     * 参数描述 - 包含所有约束、默认值、示例等信息
     */
    val value: String
)

/**
 * MCP服务器配置注解
 * 
 * 用于配置整个MCP服务器的属性。
 * 
 * 使用示例：
 * ```kotlin
 * @McpServerConfig(
 *     name = "calculator",  // 可选，默认使用类名
 *     version = "1.0.0", 
 *     description = "数学计算工具服务器"
 * )
 * class CalculatorServer : McpServerBase() {
 *     // 工具方法...
 * }
 * 
 * // 或者更简洁的写法
 * @McpServerConfig(description = "计算器服务")  // name 会自动使用 "CalculatorServer"
 * class CalculatorServer : McpServerBase() {
 *     // 工具方法...
 * }
 * ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class McpServerConfig(
    /**
     * 服务器名称，为空时使用类名
     */
    val name: String = "",
    
    /**
     * 服务器版本
     */
    val version: String = "1.0.0",
    
    /**
     * 服务器描述
     */
    val description: String = ""
)

/**
 * 工具分组注解
 * 
 * 用于将相关工具归类到同一组中，便于管理和文档化。
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ToolGroup(
    /**
     * 分组名称
     */
    val group: String,
    
    /**
     * 分组描述
     */
    val description: String = ""
)

/**
 * 工具权限注解
 * 
 * 用于标记工具需要的权限级别，可用于安全检查。
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class RequiresPermission(
    /**
     * 所需权限列表
     */
    val permissions: Array<String>,
    
    /**
     * 权限检查策略
     */
    val strategy: PermissionStrategy = PermissionStrategy.ALL_REQUIRED
)

/**
 * 权限检查策略
 */
enum class PermissionStrategy {
    /**
     * 需要所有权限
     */
    ALL_REQUIRED,
    
    /**
     * 只需要任意一个权限
     */
    ANY_REQUIRED,
    
    /**
     * 仅用于记录，不强制检查
     */
    DOCUMENTATION_ONLY
}

/**
 * 弃用工具注解
 * 
 * 标记已弃用的工具方法。
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class DeprecatedTool(
    /**
     * 弃用原因
     */
    val reason: String,
    
    /**
     * 替代方案
     */
    val replaceWith: String = "",
    
    /**
     * 计划移除的版本
     */
    val removeInVersion: String = ""
)

/**
 * 工具速率限制注解
 * 
 * 用于限制工具调用的频率。
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class RateLimit(
    /**
     * 每分钟最大调用次数
     */
    val maxCallsPerMinute: Int = 60,
    
    /**
     * 每小时最大调用次数
     */
    val maxCallsPerHour: Int = 3600
)

/**
 * 实验性工具注解
 * 
 * 标记实验性或不稳定的工具。
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ExperimentalTool(
    /**
     * 实验性说明
     */
    val message: String = "此工具处于实验阶段，API可能会发生变化"
)