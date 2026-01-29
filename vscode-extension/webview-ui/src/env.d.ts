/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}

interface VsCodeApi {
  postMessage(message: any): void
  getState(): any
  setState(state: any): void
}

declare function acquireVsCodeApi(): VsCodeApi
