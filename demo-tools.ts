// 工具演示文件 - 展示 Edit、Write、Read 功能
export class ToolDemo {
  private name: string
  private version: string = '5.0.0'

  constructor(name: string) {
    this.name = name
  }

  greet(message?: string) {
    const msg = message || '你好'
    console.log(`[${new Date().toLocaleTimeString()}] ${msg} 来自 ${this.name} (版本 ${this.version})`)
  }

  farewell() {
    console.log(`[${new Date().toLocaleTimeString()}] 再见，来自 ${this.name} (版本 ${this.version})`)
  }

  getInfo() {
    return {
      name: this.name,
      version: this.version,
      timestamp: new Date().toISOString()
    }
  }
}
