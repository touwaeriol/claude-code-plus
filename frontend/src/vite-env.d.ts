/// <reference types="vite/client" />

// SVG 文件类型声明
declare module '*.svg' {
  const content: string
  export default content
}

// SVG 原始内容导入（?raw）
declare module '*.svg?raw' {
  const content: string
  export default content
}

// SVG 作为组件导入（?component）
declare module '*.svg?component' {
  import { DefineComponent } from 'vue'
  const component: DefineComponent
  export default component
}
