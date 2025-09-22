package com.claudecodeplus.demo

/**
 * TodoWrite 工具演示
 *
 * 这个文件演示了如何使用 TodoWrite 工具来管理开发任务
 * TodoWrite 工具帮助跟踪任务进度，让用户了解当前的工作状态
 */

// 这里将实现一个简单的计算器类来演示任务管理

class SimpleCalculator {

    fun add(a: Double, b: Double): Double {
        return a + b
    }

    fun subtract(a: Double, b: Double): Double {
        return a - b
    }

    fun multiply(a: Double, b: Double): Double {
        return a * b
    }

    fun divide(a: Double, b: Double): Double {
        if (b == 0.0) {
            throw IllegalArgumentException("除数不能为零")
        }
        return a / b
    }

    fun power(base: Double, exponent: Double): Double {
        return Math.pow(base, exponent)
    }
}