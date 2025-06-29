package com.claudecodeplus.sdk

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnsiProcessorTest {
    
    @Test
    fun testDetectWindowTitleSequence() {
        val input = "\u001B]0;claude\u0007{\"type\":\"system\"}"
        val sequences = AnsiProcessor.detectAnsiSequences(input)
        
        assertEquals(1, sequences.size)
        assertEquals(AnsiProcessor.AnsiType.WINDOW_TITLE, sequences[0].type)
        assertEquals("\u001B]0;claude\u0007", sequences[0].sequence)
        assertTrue(sequences[0].description.contains("claude"))
    }
    
    @Test
    fun testCleanAnsiSequences() {
        val input = "\u001B]0;claude\u0007{\"type\":\"system\",\"content\":\"test\"}"
        val cleaned = AnsiProcessor.cleanAnsiSequences(input)
        
        assertEquals("{\"type\":\"system\",\"content\":\"test\"}", cleaned)
        assertFalse(AnsiProcessor.containsAnsiSequences(cleaned))
    }
    
    @Test
    fun testDetectColorSequences() {
        val input = "\u001B[31mRed text\u001B[0m normal"
        val sequences = AnsiProcessor.detectAnsiSequences(input)
        
        assertEquals(2, sequences.size)
        assertEquals(AnsiProcessor.AnsiType.COLOR, sequences[0].type)
        assertEquals(AnsiProcessor.AnsiType.COLOR, sequences[1].type)
    }
    
    @Test
    fun testNoAnsiSequences() {
        val input = "{\"type\":\"assistant\",\"message\":\"hello\"}"
        val sequences = AnsiProcessor.detectAnsiSequences(input)
        val cleaned = AnsiProcessor.cleanAnsiSequences(input)
        
        assertEquals(0, sequences.size)
        assertEquals(input, cleaned)
        assertFalse(AnsiProcessor.containsAnsiSequences(input))
    }
    
    @Test
    fun testRealClaudeOutput() {
        // 模拟真实的Claude CLI输出
        val input = "\u001B]0;claude\u0007{\"type\":\"system\",\"subtype\":\"init\",\"session_id\":\"test\"}"
        val cleaned = AnsiProcessor.cleanAnsiSequences(input)
        
        assertEquals("{\"type\":\"system\",\"subtype\":\"init\",\"session_id\":\"test\"}", cleaned)
        
        // 验证清理后的内容可以被JSON解析
        // 这里可以添加JSON解析验证
    }
}