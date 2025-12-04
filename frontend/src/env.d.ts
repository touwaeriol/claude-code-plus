/// <reference types="vite/client" />

// Vue Virtual Scroller
declare module 'vue-virtual-scroller' {
  import type { DefineComponent, ComponentPublicInstance } from 'vue'

  interface DynamicScrollerInstance extends ComponentPublicInstance {
    scrollToBottom(): void
    forceUpdate(): void
    $el: HTMLElement
  }

  export const DynamicScroller: DefineComponent<{
    items: unknown[]
    keyField?: string
    direction?: 'vertical' | 'horizontal'
    listClass?: string
    itemClass?: string
    minItemSize?: number
    buffer?: number
  }, DynamicScrollerInstance>

  export const DynamicScrollerItem: DefineComponent<{
    item: unknown
    active?: boolean
    sizeDependencies?: unknown[]
    watchData?: boolean
  }>

  export const RecycleScroller: DefineComponent<{
    items: unknown[]
    keyField?: string
    direction?: 'vertical' | 'horizontal'
    itemSize?: number | null
    minItemSize?: number
    sizeField?: string
    typeField?: string
    buffer?: number
    pageMode?: boolean
    prerender?: number
    emitUpdate?: boolean
    listClass?: string
    itemClass?: string
    itemTag?: string
    listTag?: string
  }>
}

// Window extensions
interface Window {
  __serverUrl?: string
  __pageUrl?: string
  __projectPath?: string
}
