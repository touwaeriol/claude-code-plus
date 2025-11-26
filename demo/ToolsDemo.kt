package demo

/**
 * 工具演示类
 * 用于展示 Claude Code Plus 的各种工具功能
 */
class ToolsDemo {
    // 临时变量名，待重构
    private val configData = "临时数据"

    fun greet(name: String): String {
        return "Hello, $name!"
    }

    fun calculate(a: Int, b: Int): Int {
        return a + b
    }

    fun displayConfig() {
        println("临时变量: $configData")
    }
}
