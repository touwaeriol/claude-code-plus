package com.claudecodeplus.mcp.services

import com.claudecodeplus.mcp.models.*
import com.intellij.openapi.project.Project
import java.util.concurrent.Future

/**
 * 分析服务工厂
 * 根据配置选择合适的实现方式
 */
object AnalysisServiceFactory {
    
    enum class ImplementationType {
        IDE_BASIC,   // 基础 IDE 实现（基于文件系统和文本分析）
        INTELLIJ_API // 完整 IntelliJ API 实现
    }
    
    /**
     * 创建分析服务实例
     */
    fun createAnalysisService(
        project: Project,
        type: ImplementationType = ImplementationType.INTELLIJ_API
    ): AnalysisService {
        return when (type) {
            ImplementationType.IDE_BASIC -> IdeAnalysisServiceWrapper(project)
            ImplementationType.INTELLIJ_API -> IntelliJNativeAnalysisServiceWrapper(project)
        }
    }
}

/**
 * 统一的分析服务接口
 */
interface AnalysisService {
    fun checkFileErrors(filePath: String, checkLevel: String): Future<FileErrorCheckResult>
    fun analyzeCodeQuality(filePath: String, metrics: List<String>): Future<CodeQualityResult>
    fun validateSyntax(filePath: String): Future<SyntaxValidationResult>
    fun getImplementationType(): AnalysisServiceFactory.ImplementationType
    fun getCapabilities(): ServiceCapabilities
}

/**
 * 服务能力描述
 */
data class ServiceCapabilities(
    val supportsNativeLanguageDetection: Boolean,
    val supportsIntelliJInspections: Boolean,
    val supportsPsiAnalysis: Boolean,
    val supportsLanguageSpecificAnalysis: Boolean,
    val requiresIdeEnvironment: Boolean,
    val supportedLanguages: List<String>
)

/**
 * 基础 IDE 实现的包装器
 */
private class IdeAnalysisServiceWrapper(project: Project) : AnalysisService {
    private val service = IdeAnalysisService(project)
    
    override fun checkFileErrors(filePath: String, checkLevel: String) = 
        service.checkFileErrors(filePath, checkLevel)
    
    override fun analyzeCodeQuality(filePath: String, metrics: List<String>) = 
        service.analyzeCodeQuality(filePath, metrics)
    
    override fun validateSyntax(filePath: String) = 
        service.validateSyntax(filePath)
    
    override fun getImplementationType() = AnalysisServiceFactory.ImplementationType.IDE_BASIC
    
    override fun getCapabilities() = ServiceCapabilities(
        supportsNativeLanguageDetection = false,
        supportsIntelliJInspections = false,
        supportsPsiAnalysis = false,
        supportsLanguageSpecificAnalysis = false,
        requiresIdeEnvironment = false,
        supportedLanguages = listOf("Kotlin", "Java", "Python", "JavaScript", "TypeScript", "Go", "Rust")
    )
}

/**
 * IntelliJ API 实现的包装器
 * ✅ 使用完全依赖 IDE 平台的优化实现
 */
private class IntelliJNativeAnalysisServiceWrapper(project: Project) : AnalysisService {
    // ✅ 使用优化后的 IntelliJ API 服务
    private val service = IntelliJNativeAnalysisService(project)
    
    override fun checkFileErrors(filePath: String, checkLevel: String) = 
        service.checkFileErrors(filePath, checkLevel)
    
    override fun analyzeCodeQuality(filePath: String, metrics: List<String>) = 
        service.analyzeCodeQuality(filePath, metrics)
    
    override fun validateSyntax(filePath: String) = 
        service.validateSyntax(filePath)
    
    override fun getImplementationType() = AnalysisServiceFactory.ImplementationType.INTELLIJ_API
    
    override fun getCapabilities() = ServiceCapabilities(
        supportsNativeLanguageDetection = true,
        supportsIntelliJInspections = true,
        supportsPsiAnalysis = true,
        supportsLanguageSpecificAnalysis = true,
        requiresIdeEnvironment = true,
        supportedLanguages = listOf("All languages supported by IntelliJ Platform")
    )
}