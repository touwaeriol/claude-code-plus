package com.claudecodeplus.ui.services

import java.util.*

/**
 * 国际化服务
 * 负责管理应用的多语言支持，包括从IDE获取语言设置和映射到支持的语言
 */
object LocalizationService {
    
    /**
     * 支持的语言列表
     */
    enum class SupportedLanguage(
        val code: String,
        val displayName: String,
        val nativeName: String
    ) {
        ENGLISH("en", "English", "English"),
        SIMPLIFIED_CHINESE("zh-CN", "Simplified Chinese", "简体中文"),
        TRADITIONAL_CHINESE("zh-TW", "Traditional Chinese", "繁體中文"),
        JAPANESE("ja", "Japanese", "日本語"),
        KOREAN("ko", "Korean", "한국어");
        
        companion object {
            fun fromCode(code: String): SupportedLanguage {
                return values().find { it.code == code } ?: ENGLISH
            }
        }
    }
    
    /**
     * 从IDE获取当前语言设置
     * 注意：在toolwindow模块中，暂时使用系统默认语言
     * 后续可以通过插件模块传递真正的IDE语言设置
     * @return 当前系统的Locale设置
     */
    fun getIDELocale(): Locale {
        return try {
            // 暂时使用系统默认语言，后续可以从插件模块获取IDE语言设置
            val systemLocale = Locale.getDefault()
            println("[LocalizationService] 使用系统默认语言: $systemLocale")
            systemLocale
        } catch (e: Exception) {
            // 如果获取失败，返回默认英语
            println("[LocalizationService] 获取系统语言设置失败: ${e.message}")
            Locale.ENGLISH
        }
    }
    
    /**
     * 将IDE的Locale映射到支持的语言
     * @param locale IDE的Locale设置
     * @return 映射后的支持语言
     */
    fun mapToSupportedLanguage(locale: Locale): SupportedLanguage {
        return when {
            locale.language == "zh" && locale.country == "CN" -> SupportedLanguage.SIMPLIFIED_CHINESE
            locale.language == "zh" && locale.country == "TW" -> SupportedLanguage.TRADITIONAL_CHINESE
            locale.language == "zh" && locale.country == "HK" -> SupportedLanguage.TRADITIONAL_CHINESE
            locale.language == "ja" -> SupportedLanguage.JAPANESE
            locale.language == "ko" -> SupportedLanguage.KOREAN
            else -> SupportedLanguage.ENGLISH // 默认英语
        }
    }
    
    /**
     * 获取当前应用应该使用的语言
     * @return 当前应用语言
     */
    fun getCurrentLanguage(): SupportedLanguage {
        val ideLocale = getIDELocale()
        val supportedLanguage = mapToSupportedLanguage(ideLocale)
        
        println("[LocalizationService] IDE Locale: $ideLocale -> 应用语言: ${supportedLanguage.nativeName}")
        
        return supportedLanguage
    }
    
    /**
     * 获取语言代码用于资源系统
     * @return 资源系统使用的语言代码
     */
    fun getResourceLanguageCode(): String {
        return getCurrentLanguage().code
    }
    
    /**
     * 检查是否为中日韩语言（需要不同的字体渲染）
     * @param language 要检查的语言
     * @return 是否为CJK语言
     */
    fun isCJKLanguage(language: SupportedLanguage = getCurrentLanguage()): Boolean {
        return when (language) {
            SupportedLanguage.SIMPLIFIED_CHINESE,
            SupportedLanguage.TRADITIONAL_CHINESE,
            SupportedLanguage.JAPANESE,
            SupportedLanguage.KOREAN -> true
            SupportedLanguage.ENGLISH -> false
        }
    }
    
    /**
     * 获取所有支持的语言列表
     * @return 支持的语言列表
     */
    fun getSupportedLanguages(): List<SupportedLanguage> {
        return SupportedLanguage.values().toList()
    }
    
    /**
     * 格式化带参数的字符串
     * @param template 模板字符串（如 "文件大小：%1\$s"）
     * @param args 参数列表
     * @return 格式化后的字符串
     */
    fun formatString(template: String, vararg args: Any): String {
        return try {
            String.format(template, *args)
        } catch (e: Exception) {
            // 如果格式化失败，返回原始模板
            println("[LocalizationService] 字符串格式化失败: $template, 错误: ${e.message}")
            template
        }
    }
}