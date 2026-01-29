// VS Code API 封装

interface VSCodeApi {
  postMessage(message: any): void
  getState(): any
  setState(state: any): void
}

class VSCodeBridge {
  private _vscode: VSCodeApi | null = null
  
  private get api(): VSCodeApi {
    if (!this._vscode) {
      // 尝试获取 VS Code API
      if (typeof acquireVsCodeApi !== 'undefined') {
        this._vscode = acquireVsCodeApi()
      } else {
        // 开发模式下的模拟 API
        console.warn('VS Code API not available, using mock')
        this._vscode = {
          postMessage: (message: any) => {
            console.log('Mock postMessage:', message)
          },
          getState: () => ({}),
          setState: (state: any) => {
            console.log('Mock setState:', state)
          }
        }
      }
    }
    return this._vscode
  }
  
  // 发送消息到扩展
  postMessage(message: any) {
    this.api.postMessage(message)
  }
  
  // 保存设置
  saveSetting(key: string, value: any) {
    this.postMessage({ type: 'saveSetting', payload: { key, value } })
  }
  
  // 浏览文件
  browseFile(settingKey: string) {
    this.postMessage({ type: 'browseFile', payload: { settingKey } })
  }
  
  // 获取设置
  getSettings() {
    this.postMessage({ type: 'getSettings' })
  }
  
  // 获取状态
  getState() {
    return this.api.getState()
  }
  
  // 设置状态
  setState(state: any) {
    this.api.setState(state)
  }
  
  // 监听来自扩展的消息
  onMessage(callback: (event: MessageEvent) => void) {
    window.addEventListener('message', callback)
    return () => window.removeEventListener('message', callback)
  }
}

export const vscode = new VSCodeBridge()
