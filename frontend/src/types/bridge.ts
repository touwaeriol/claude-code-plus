/**
 * 前后端通信协议类型定义
 */

// 前端请求格式
export interface FrontendRequest {
  action: string
  data?: any
}

// 后端响应格式
export interface FrontendResponse {
  success: boolean
  data?: any
  error?: string
}

// 后端推送事件格式
export interface IdeEvent {
  type: string
  data?: any
}

// IDE 主题定义（与 themeService.ThemeColors 保持一致）
export interface IdeTheme {
  background: string
  foreground: string
  borderColor: string
  panelBackground: string
  textFieldBackground: string
  selectionBackground: string
  selectionForeground: string
  linkColor: string
  errorColor: string
  warningColor: string
  successColor: string
  separatorColor: string
  hoverBackground: string
  accentColor: string
  infoBackground: string
  codeBackground: string
  secondaryForeground: string
}

// 全局类型扩展
declare global {
  interface Window {
    ideaBridge: {
      query: (action: string, data?: any) => Promise<FrontendResponse>
    }
    onIdeEvent: (event: IdeEvent) => void
    __bridgeReady: boolean
  }
}

export {}
