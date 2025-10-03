package com.example

/**
 * 这是一个演示文件
 * 用于展示各种工具的使用
 */
class Example {
    private val name = "Claude Code Plus"
    private val version = "2.0"
    private val author = "AI Assistant"

    fun greet() {
        println("Hello from $name version $version")
    }

    fun calculate(a: Int, b: Int): Int {
        // 支持加法运算
        val result = a + b
        println("计算结果: $a + $b = $result")
        return result
    }
}
