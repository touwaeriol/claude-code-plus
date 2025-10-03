package com.claudecodeplus.demo

/**
 * 演示类 - 用于展示 Edit 工具功能
 */
class DemoService {
    private var counter = 0
    private val logger = System.getLogger("DemoService")

    fun greet(name: CharSequence): CharSequence {
        counter++
        return "Hello, $name! (count: $counter)"
    }

    fun calculate(a: Int, b: Int): Int {
        // 支持多种运算
        return when {
            a > b -> a - b
            a < b -> a + b
            else -> a * b
        }
    }

    fun processData(items: List<CharSequence>): List<CharSequence> {
        return items.map { it.uppercase() }
    }
}
