/**
 * 工具演示文件
 *
 * 这个文件用于演示 Read、Write、Edit 工具的使用
 */

// 示例函数
function greet(name: string): string {
  return `Hello, ${name}!`
}

// 新增：告别函数
function farewell(name: string): string {
  return `Goodbye, ${name}! See you next time.`
}

// 示例常量
const VERSION = '1.0.0'

// 导出
export { greet, farewell, VERSION }
