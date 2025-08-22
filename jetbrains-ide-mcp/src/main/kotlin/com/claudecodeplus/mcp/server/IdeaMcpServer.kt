package com.claudecodeplus.mcp.server

import com.claudecodeplus.mcp.models.*
import com.claudecodeplus.mcp.services.IntelliJNativeAnalysisService
import com.intellij.openapi.project.Project
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.request.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.contentnegotiation.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.measureTimeMillis

/**
 * IntelliJ IDE MCP 服务器
 * 基于 IntelliJ Platform API 实现，完全依赖 IDE 平台功能
 */
class IdeaMcpServer(
    private val project: Project,
    private val port: Int = 8001
) {
    private val logger = LoggerFactory.getLogger(IdeaMcpServer::class.java)
    val isRunning = AtomicBoolean(false)
    private var server: ApplicationEngine? = null
    private var serverJob: Job? = null
    
    // 核心分析服务
    private val analysisService = IntelliJNativeAnalysisService(project)
    
    /**
     * 启动 MCP 服务器
     */
    suspend fun start() = withContext(Dispatchers.IO) {
        if (isRunning.get()) {
            logger.warn("MCP 服务器已在运行，端口: $port")
            return@withContext
        }
        
        try {
            logger.info("正在启动 IntelliJ IDE MCP 服务器，端口: $port")
            
            server = embeddedServer(Netty, port = port, host = "localhost") {
                install(ContentNegotiation) {
                    json(Json {
                        prettyPrint = true
                        isLenient = true
                        ignoreUnknownKeys = true
                        encodeDefaults = true
                    })
                }
                
                routing {
                    // 服务器信息
                    get("/") {
                        call.respond(createServerInfo())
                    }
                    
                    // 健康检查
                    get("/health") {
                        call.respond(mapOf(
                            "status" to "healthy",
                            "timestamp" to System.currentTimeMillis(),
                            "project" to project.name
                        ))
                    }
                    
                    // 工具定义
                    get("/tools") {
                        call.respond(createToolDefinitions())
                    }
                    
                    // MCP 工具路由
                    route("/tools") {
                        // 文件错误检查
                        post("/check_file_errors") {
                            try {
                                val request = call.receive<CheckFileErrorsRequest>()
                                val result = executeCheckFileErrors(request)
                                val response = McpToolResponse(
                                    tool = "check_file_errors",
                                    success = true,
                                    result = result,
                                    metadata = McpMetadata()
                                )
                                call.respond(response)
                            } catch (e: Exception) {
                                logger.error("check_file_errors 执行失败", e)
                                call.respond(McpToolResponse<Any>(
                                    tool = "check_file_errors",
                                    success = false,
                                    error = McpError(
                                        code = e.javaClass.simpleName,
                                        message = e.message ?: "Unknown error"
                                    ),
                                    metadata = McpMetadata()
                                ))
                            }
                        }
                        
                        // 代码质量分析
                        post("/analyze_code_quality") {
                            try {
                                val request = call.receive<AnalyzeCodeQualityRequest>()
                                val result = executeAnalyzeCodeQuality(request)
                                val response = McpToolResponse(
                                    tool = "analyze_code_quality",
                                    success = true,
                                    result = result,
                                    metadata = McpMetadata()
                                )
                                call.respond(response)
                            } catch (e: Exception) {
                                logger.error("analyze_code_quality 执行失败", e)
                                call.respond(McpToolResponse<Any>(
                                    tool = "analyze_code_quality",
                                    success = false,
                                    error = McpError(
                                        code = e.javaClass.simpleName,
                                        message = e.message ?: "Unknown error"
                                    ),
                                    metadata = McpMetadata()
                                ))
                            }
                        }
                        
                        // 语法验证
                        post("/validate_syntax") {
                            try {
                                val request = call.receive<ValidateSyntaxRequest>()
                                val result = executeValidateSyntax(request)
                                val response = McpToolResponse(
                                    tool = "validate_syntax",
                                    success = true,
                                    result = result,
                                    metadata = McpMetadata()
                                )
                                call.respond(response)
                            } catch (e: Exception) {
                                logger.error("validate_syntax 执行失败", e)
                                call.respond(McpToolResponse<Any>(
                                    tool = "validate_syntax",
                                    success = false,
                                    error = McpError(
                                        code = e.javaClass.simpleName,
                                        message = e.message ?: "Unknown error"
                                    ),
                                    metadata = McpMetadata()
                                ))
                            }
                        }
                    }
                }
            }
            
            serverJob = GlobalScope.launch {
                server?.start(wait = false)
                isRunning.set(true)
                logger.info("IntelliJ IDE MCP 服务器已启动: http://localhost:$port")
            }
            
            delay(1000) // 等待服务器启动完成
            
        } catch (e: Exception) {
            logger.error("启动 MCP 服务器失败", e)
            throw e
        }
    }
    
    /**
     * 停止服务器
     */
    suspend fun stop() {
        if (!isRunning.get()) {
            return
        }
        
        try {
            logger.info("正在停止 IntelliJ IDE MCP 服务器...")
            
            serverJob?.cancelAndJoin()
            server?.stop(1000, 2000)
            
            isRunning.set(false)
            logger.info("IntelliJ IDE MCP 服务器已停止")
            
        } catch (e: Exception) {
            logger.error("停止 MCP 服务器时出现错误", e)
        }
    }
    
    // 删除有问题的通用处理器，直接在路由中处理
    
    /**
     * 执行文件错误检查
     */
    private suspend fun executeCheckFileErrors(request: CheckFileErrorsRequest): FileErrorCheckResult {
        logger.info("执行文件错误检查: ${request.filePath}")
        
        return analysisService.checkFileErrors(request.filePath, request.checkLevel).get()
    }
    
    /**
     * 执行代码质量分析
     */
    private suspend fun executeAnalyzeCodeQuality(request: AnalyzeCodeQualityRequest): CodeQualityResult {
        logger.info("执行代码质量分析: ${request.filePath}")
        
        return analysisService.analyzeCodeQuality(request.filePath, request.metrics).get()
    }
    
    /**
     * 执行语法验证
     */
    private suspend fun executeValidateSyntax(request: ValidateSyntaxRequest): SyntaxValidationResult {
        logger.info("执行语法验证: ${request.filePath}")
        
        return analysisService.validateSyntax(request.filePath).get()
    }
    
    // 服务器配置方法
    
    private fun createServerInfo() = mapOf(
        "server" to "jetbrains-ide-mcp",
        "version" to "1.0.0",
        "description" to "IntelliJ Platform MCP Server for Code Analysis",
        "project" to mapOf(
            "name" to project.name,
            "path" to (project.basePath ?: "unknown")
        ),
        "status" to "running",
        "timestamp" to System.currentTimeMillis()
    )
    
    private fun createToolDefinitions() = mapOf(
        "tools" to listOf(
            mapOf(
                "name" to "check_file_errors",
                "description" to "检查文件的语法错误和代码问题"
            ),
            mapOf(
                "name" to "analyze_code_quality",
                "description" to "分析代码质量指标"
            ),
            mapOf(
                "name" to "validate_syntax",
                "description" to "验证文件语法正确性"
            )
        )
    )
}