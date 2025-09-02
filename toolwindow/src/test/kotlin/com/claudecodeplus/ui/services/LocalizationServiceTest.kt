package com.claudecodeplus.ui.services

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.util.*

class LocalizationServiceTest {
    
    @Test
    fun `test language mapping`() {
        // 测试中文映射
        val zhCN = LocalizationService.mapToSupportedLanguage(Locale.SIMPLIFIED_CHINESE)
        assertEquals(LocalizationService.SupportedLanguage.SIMPLIFIED_CHINESE, zhCN)
        
        val zhTW = LocalizationService.mapToSupportedLanguage(Locale.TRADITIONAL_CHINESE)
        assertEquals(LocalizationService.SupportedLanguage.TRADITIONAL_CHINESE, zhTW)
        
        // 测试日韩语映射
        val ja = LocalizationService.mapToSupportedLanguage(Locale.JAPANESE)
        assertEquals(LocalizationService.SupportedLanguage.JAPANESE, ja)
        
        val ko = LocalizationService.mapToSupportedLanguage(Locale.KOREAN)
        assertEquals(LocalizationService.SupportedLanguage.KOREAN, ko)
        
        // 测试默认映射
        val en = LocalizationService.mapToSupportedLanguage(Locale.ENGLISH)
        assertEquals(LocalizationService.SupportedLanguage.ENGLISH, en)
        
        val fr = LocalizationService.mapToSupportedLanguage(Locale.FRENCH)
        assertEquals(LocalizationService.SupportedLanguage.ENGLISH, fr) // 默认英语
    }
    
    @Test
    fun `test CJK language detection`() {
        assertTrue(LocalizationService.isCJKLanguage(LocalizationService.SupportedLanguage.SIMPLIFIED_CHINESE))
        assertTrue(LocalizationService.isCJKLanguage(LocalizationService.SupportedLanguage.TRADITIONAL_CHINESE))
        assertTrue(LocalizationService.isCJKLanguage(LocalizationService.SupportedLanguage.JAPANESE))
        assertTrue(LocalizationService.isCJKLanguage(LocalizationService.SupportedLanguage.KOREAN))
        assertFalse(LocalizationService.isCJKLanguage(LocalizationService.SupportedLanguage.ENGLISH))
    }
    
    @Test
    fun `test string formatting`() {
        val template = "文件大小：%1\$s"
        val result = LocalizationService.formatString(template, "1.2MB")
        assertEquals("文件大小：1.2MB", result)
        
        // 测试格式化失败的情况
        val invalidTemplate = "无效格式"
        val fallback = LocalizationService.formatString(invalidTemplate, "参数")
        assertEquals("无效格式", fallback)
    }
}

class StringResourcesTest {
    
    @Test
    fun `test string resource retrieval`() {
        // 测试英语
        val enPlaceholder = StringResources.getString("chat_input_placeholder", LocalizationService.SupportedLanguage.ENGLISH)
        assertEquals("Type a message...", enPlaceholder)
        
        // 测试简体中文
        val cnPlaceholder = StringResources.getString("chat_input_placeholder", LocalizationService.SupportedLanguage.SIMPLIFIED_CHINESE)
        assertEquals("输入消息...", cnPlaceholder)
        
        // 测试繁体中文
        val twPlaceholder = StringResources.getString("chat_input_placeholder", LocalizationService.SupportedLanguage.TRADITIONAL_CHINESE)
        assertEquals("輸入訊息...", twPlaceholder)
        
        // 测试日语
        val jaPlaceholder = StringResources.getString("chat_input_placeholder", LocalizationService.SupportedLanguage.JAPANESE)
        assertEquals("メッセージを入力...", jaPlaceholder)
        
        // 测试韩语
        val koPlaceholder = StringResources.getString("chat_input_placeholder", LocalizationService.SupportedLanguage.KOREAN)
        assertEquals("메시지를 입력하세요...", koPlaceholder)
    }
    
    @Test
    fun `test fallback to English`() {
        // 测试不存在的键
        val missing = StringResources.getString("non_existent_key", LocalizationService.SupportedLanguage.SIMPLIFIED_CHINESE)
        assertEquals("non_existent_key", missing)
        
        // 测试存在于英语但不存在于其他语言的键
        val englishOnly = StringResources.getString("send", LocalizationService.SupportedLanguage.ENGLISH)
        assertEquals("Send", englishOnly)
    }
    
    @Test
    fun `test convenience functions`() {
        // 由于 stringResource() 使用当前语言，我们主要测试它不会崩溃
        val result = stringResource("send")
        assertNotNull(result)
        assertTrue(result.isNotEmpty())
        
        // 测试格式化函数
        val formatted = formatStringResource("send")
        assertNotNull(formatted)
        assertTrue(formatted.isNotEmpty())
    }
}