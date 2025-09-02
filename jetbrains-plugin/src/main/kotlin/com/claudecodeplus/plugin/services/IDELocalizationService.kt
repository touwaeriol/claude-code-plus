package com.claudecodeplus.plugin.services

import com.intellij.DynamicBundle
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import java.util.*

/**
 * IDE 国际化服务
 * 在插件模块中获取真正的IDE语言设置
 */
@Service(Service.Level.APP)
class IDELocalizationService {
    
    companion object {
        private val logger = Logger.getInstance(IDELocalizationService::class.java)
    }
    
    /**
     * 获取IDE当前语言设置
     * @return IDE的Locale设置
     */
    fun getIDELocale(): Locale {
        return try {
            val locale = DynamicBundle.getLocale()
            logger.info("IDELocalizationService: 获取IDE语言设置: $locale")
            locale
        } catch (e: Exception) {
            logger.warn("IDELocalizationService: 获取IDE语言设置失败", e)
            Locale.getDefault()
        }
    }
    
    /**
     * 获取语言代码字符串
     * @return 语言代码（如 "zh-CN", "en", "ja" 等）
     */
    fun getLanguageCode(): String {
        val locale = getIDELocale()
        return when {
            locale.language == "zh" && locale.country == "CN" -> "zh-CN"
            locale.language == "zh" && locale.country == "TW" -> "zh-TW"
            locale.language == "zh" && locale.country == "HK" -> "zh-TW"
            locale.language == "ja" -> "ja"
            locale.language == "ko" -> "ko"
            else -> "en" // 默认英语
        }
    }
    
    /**
     * 获取语言显示名称
     * @return 语言的本地化显示名称
     */
    fun getLanguageDisplayName(): String {
        val locale = getIDELocale()
        return locale.getDisplayLanguage(locale)
    }
    
    /**
     * 检查是否为中日韩语言
     * @return 是否为CJK语言
     */
    fun isCJKLanguage(): Boolean {
        val languageCode = getLanguageCode()
        return languageCode in setOf("zh-CN", "zh-TW", "ja", "ko")
    }
}