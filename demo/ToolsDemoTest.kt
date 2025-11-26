package demo

import org.junit.Test

/**
 * 工具演示测试类
 */
class ToolsDemoTest {

    @Test
    fun testOldMethodName() {
        val demo = ToolsDemo()
        demo.oldMethodName()
    }

    @Test
    fun testProcessData() {
        val demo = ToolsDemo()
        val result = demo.processData("测试数据")
        assert(result.contains("测试数据"))
    }

    @Test
    fun testTempVariable() {
        val demo = ToolsDemo()
        demo.displayConfig()
        // 这里使用了 configData 相关的功能
    }
}
