/**
 * 浏览器安全工具（自适应双模式）
 *
 * ════════════════════════════════════════════════════════════════════════════
 * 架构说明
 * ════════════════════════════════════════════════════════════════════════════
 *
 * 本工具根据运行环境自动选择安全策略：
 *
 * 【IDE 模式】(window.__serverUrl 存在)
 *   JCEF 层: CefRequestHandler.onBeforeBrowse() - 100% 可靠拦截
 *   JS 层:   链接分类 + 文件打开 + 右键菜单
 *
 * 【浏览器模式】(window.__serverUrl 不存在)
 *   JS 层:   链接点击 + window.open + location Proxy + 表单 + 拖拽 + 右键菜单
 *   注意:    location.assign/replace 无法完全拦截（浏览器安全限制）
 *
 * ════════════════════════════════════════════════════════════════════════════
 */

// ════════════════════════════════════════════════════════════════════════════
// 类型定义
// ════════════════════════════════════════════════════════════════════════════

export type LinkType = 'external' | 'file' | 'anchor' | 'unknown'

type ContextMenuHandler = (event: MouseEvent) => void
type OpenFileCallback = (filePath: string) => void
type TranslateFunction = (key: string) => string

export interface BrowserSecurityOptions {
  allowFileLinks?: boolean
  disableContextMenu?: boolean
  showCustomContextMenu?: boolean
}

export interface LinkClickOptions {
  openFile?: OpenFileCallback
}

interface ContextMenuItem {
  id: string
  labelKey: string
  icon: string
  shortcut?: string
  action: () => void
  visible: () => boolean
  enabled: () => boolean
}

// ════════════════════════════════════════════════════════════════════════════
// 状态管理
// ════════════════════════════════════════════════════════════════════════════

const detectIdeMode = (): boolean => !!(window as any).__serverUrl

const defaultOptions: Required<BrowserSecurityOptions> = {
  allowFileLinks: true,
  disableContextMenu: true,
  showCustomContextMenu: true,
}

let currentOptions = { ...defaultOptions }
let customContextMenuHandler: ContextMenuHandler | null = null
let globalOpenFileCallback: OpenFileCallback | null = null
let translateFn: TranslateFunction | null = null
let initialized = false
let currentContextMenu: HTMLElement | null = null
let contextMenuTarget: HTMLElement | null = null

// 保存事件监听器引用，用于清理（防止内存泄漏）
let linkClickHandler: ((event: MouseEvent) => void) | null = null
let hideContextMenuClickHandler: ((event: Event) => void) | null = null
let hideContextMenuScrollHandler: ((event: Event) => void) | null = null
let hideContextMenuResizeHandler: (() => void) | null = null
let hideContextMenuBlurHandler: (() => void) | null = null

const defaultTranslations: Record<string, Record<string, string>> = {
  'zh-CN': { 'contextMenu.copy': '复制', 'contextMenu.paste': '粘贴', 'contextMenu.cut': '剪切', 'contextMenu.selectAll': '全选' },
  'zh-TW': { 'contextMenu.copy': '複製', 'contextMenu.paste': '貼上', 'contextMenu.cut': '剪下', 'contextMenu.selectAll': '全選' },
  'en-US': { 'contextMenu.copy': 'Copy', 'contextMenu.paste': 'Paste', 'contextMenu.cut': 'Cut', 'contextMenu.selectAll': 'Select All' },
  'ja-JP': { 'contextMenu.copy': 'コピー', 'contextMenu.paste': '貼り付け', 'contextMenu.cut': '切り取り', 'contextMenu.selectAll': 'すべて選択' },
  'ko-KR': { 'contextMenu.copy': '복사', 'contextMenu.paste': '붙여넣기', 'contextMenu.cut': '잘라내기', 'contextMenu.selectAll': '모두 선택' },
}

// ════════════════════════════════════════════════════════════════════════════
// 公共 API
// ════════════════════════════════════════════════════════════════════════════

/**
 * 初始化浏览器安全工具
 */
export function initBrowserSecurity(options?: BrowserSecurityOptions): void {
  if (initialized) {
    console.warn('[BrowserSecurity] Already initialized')
    return
  }

  currentOptions = { ...defaultOptions, ...options }
  const isIdeMode = detectIdeMode()

  console.log('[BrowserSecurity] Mode:', isIdeMode ? 'IDE (JCEF)' : 'Browser')

  // 链接点击处理
  setupLinkClickHandler()

  // 浏览器模式：启用 JS 层导航拦截
  if (!isIdeMode) {
    setupBrowserModeBlocking()
  }

  // 右键菜单
  if (currentOptions.disableContextMenu) {
    setupContextMenu()
  }

  // 关闭菜单的事件（保存引用以便清理）
  hideContextMenuClickHandler = hideContextMenu
  hideContextMenuScrollHandler = hideContextMenu
  hideContextMenuResizeHandler = hideContextMenu
  hideContextMenuBlurHandler = hideContextMenu
  
  document.addEventListener('click', hideContextMenuClickHandler, true)
  document.addEventListener('scroll', hideContextMenuScrollHandler, true)
  window.addEventListener('resize', hideContextMenuResizeHandler)
  window.addEventListener('blur', hideContextMenuBlurHandler)

  initialized = true
}

export function setTranslateFunction(t: TranslateFunction): void {
  translateFn = t
}

export function updateSecurityOptions(options: Partial<BrowserSecurityOptions>): void {
  currentOptions = { ...currentOptions, ...options }
}

export function getSecurityOptions(): Readonly<Required<BrowserSecurityOptions>> {
  return { ...currentOptions }
}

export function registerOpenFileCallback(callback: OpenFileCallback): void {
  globalOpenFileCallback = callback
}

export function registerContextMenuHandler(handler: ContextMenuHandler): void {
  customContextMenuHandler = handler
}

export function unregisterContextMenuHandler(): void {
  customContextMenuHandler = null
}

/**
 * 清理浏览器安全工具
 * 移除所有事件监听器，防止内存泄漏
 */
export function cleanupBrowserSecurity(): void {
  if (!initialized) return
  
  // 移除事件监听器
  if (hideContextMenuClickHandler) {
    document.removeEventListener('click', hideContextMenuClickHandler, true)
  }
  if (hideContextMenuScrollHandler) {
    document.removeEventListener('scroll', hideContextMenuScrollHandler, true)
  }
  if (hideContextMenuResizeHandler) {
    window.removeEventListener('resize', hideContextMenuResizeHandler)
  }
  if (hideContextMenuBlurHandler) {
    window.removeEventListener('blur', hideContextMenuBlurHandler)
  }
  
  // 隐藏并移除上下文菜单
  hideContextMenu()
  
  // 重置状态
  hideContextMenuClickHandler = null
  hideContextMenuScrollHandler = null
  hideContextMenuResizeHandler = null
  hideContextMenuBlurHandler = null
  linkClickHandler = null
  initialized = false
  
  console.log('[BrowserSecurity] Cleanup completed')
}

// ════════════════════════════════════════════════════════════════════════════
// 链接处理 API
// ════════════════════════════════════════════════════════════════════════════

export function getLinkType(href: string | null | undefined): LinkType {
  if (!href) return 'unknown'
  if (href.startsWith('#')) return 'anchor'
  if (href.startsWith('http://') || href.startsWith('https://')) return 'external'
  if (href.startsWith('/') || href.startsWith('./') || href.startsWith('../') ||
      href.startsWith('file://') || /^[A-Z]:[/\\]/.test(href)) return 'file'
  return 'unknown'
}

export function handleLinkClick(href: string, options?: LinkClickOptions): boolean {
  const linkType = getLinkType(href)

  switch (linkType) {
    case 'anchor':
      return false // 允许页内跳转

    case 'external':
      console.log('[BrowserSecurity] External link:', href)
      return true

    case 'file':
      if (currentOptions.allowFileLinks) {
        const filePath = href.replace('file://', '')
        const callback = options?.openFile || globalOpenFileCallback
        if (callback) {
          callback(filePath)
        } else {
          console.warn('[BrowserSecurity] No openFile callback for:', filePath)
        }
      }
      return true

    default:
      console.log('[BrowserSecurity] Unknown link:', href)
      return true
  }
}

export function handleLinkClickFromEvent(event: MouseEvent, options?: LinkClickOptions): boolean {
  const target = event.target as HTMLElement
  const anchor = target.closest('a')
  if (!anchor) return false

  const href = anchor.getAttribute('href')
  if (!href) return false

  return handleLinkClick(href, options)
}

export function safeOpenExternalUrl(url: string): void {
  if (url.startsWith('http://') || url.startsWith('https://')) {
    window.open(url, '_blank', 'noopener,noreferrer')
  }
}

// ════════════════════════════════════════════════════════════════════════════
// 导航拦截（内部实现）
// ════════════════════════════════════════════════════════════════════════════

function setupLinkClickHandler(): void {
  document.addEventListener('click', (event: MouseEvent) => {
    const target = event.target as HTMLElement
    const anchor = target.closest('a')
    if (!anchor) return

    const href = anchor.getAttribute('href')
    if (href?.startsWith('#') || href === 'javascript:void(0)' || href === 'javascript:;') return

    event.preventDefault()
    event.stopPropagation()

    if (href) handleLinkClick(href)
  }, true)
}

/**
 * 浏览器模式：启用所有 JS 层导航拦截
 */
function setupBrowserModeBlocking(): void {
  // 1. window.open
  try {
    window.open = function(url?: string | URL): Window | null {
      if (url) {
        console.log('[BrowserSecurity] Blocked window.open:', String(url))
        handleLinkClick(String(url))
      }
      return null
    }
  } catch (e) {
    console.warn('[BrowserSecurity] Could not override window.open')
  }

  // 2. location Proxy（尽力而为）
  try {
    const desc = Object.getOwnPropertyDescriptor(window, 'location')
    if (desc?.configurable) {
      const original = window.location
      Object.defineProperty(window, 'location', {
        get: () => new Proxy(original, {
          set(target, prop, value) {
            if (prop === 'href' || prop === 'pathname' || prop === 'search') {
              console.log(`[BrowserSecurity] Blocked location.${String(prop)} =`, value)
              return true
            }
            return Reflect.set(target, prop, value)
          }
        }),
        configurable: true
      })
    }
  } catch (e) {
    console.warn('[BrowserSecurity] Could not proxy location')
  }

  // 3. 表单提交
  document.addEventListener('submit', (event: Event) => {
    const form = event.target as HTMLFormElement
    const action = form.getAttribute('action')
    if (action && action !== '' && action !== '#') {
      console.log('[BrowserSecurity] Blocked form submission:', action)
      event.preventDefault()
      event.stopPropagation()
    }
  }, true)

  // 4. 拖拽导航
  document.addEventListener('dragstart', (event: DragEvent) => {
    const target = event.target as HTMLElement
    if (target.tagName === 'A' || target.tagName === 'IMG') {
      event.preventDefault()
    }
  }, true)

  document.addEventListener('drop', (e: DragEvent) => e.preventDefault(), true)
  document.addEventListener('dragover', (e: DragEvent) => e.preventDefault(), true)

  console.log('[BrowserSecurity] Browser mode blocking enabled')
}

// ════════════════════════════════════════════════════════════════════════════
// 右键菜单（内部实现）
// ════════════════════════════════════════════════════════════════════════════

function t(key: string): string {
  if (translateFn) return translateFn(key)
  const lang = navigator.language || 'en-US'
  const translations = defaultTranslations[lang] || defaultTranslations['en-US']
  return translations[key] || key
}

function isEditableElement(el: HTMLElement | null): boolean {
  if (!el) return false
  const tag = el.tagName.toUpperCase()
  return tag === 'INPUT' || tag === 'TEXTAREA' || el.getAttribute('contenteditable') === 'true'
}

function getSelectedText(): string {
  return window.getSelection()?.toString() || ''
}

function hasSelection(): boolean {
  return getSelectedText().length > 0
}

function createContextMenuElement(): HTMLElement {
  const menu = document.createElement('div')
  menu.id = 'browser-security-context-menu'
  menu.style.cssText = `
    position: fixed; z-index: 99999;
    background: var(--vscode-menu-background, #252526);
    border: 1px solid var(--vscode-menu-border, #454545);
    border-radius: 4px; box-shadow: 0 2px 8px rgba(0,0,0,0.3);
    padding: 4px 0; min-width: 160px; display: none;
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
    font-size: 13px;
  `
  document.body.appendChild(menu)
  return menu
}

function createMenuItem(item: ContextMenuItem): HTMLElement {
  const el = document.createElement('div')
  const enabled = item.enabled()

  el.style.cssText = `
    display: flex; align-items: center; justify-content: space-between;
    padding: 6px 12px; white-space: nowrap;
    cursor: ${enabled ? 'pointer' : 'default'};
    color: ${enabled ? 'var(--vscode-menu-foreground, #ccc)' : 'var(--vscode-disabledForeground, #6e6e6e)'};
  `

  const left = document.createElement('span')
  left.style.cssText = 'display: flex; align-items: center; gap: 8px;'
  left.innerHTML = `<span style="width:16px;text-align:center">${item.icon}</span><span>${t(item.labelKey)}</span>`
  el.appendChild(left)

  if (item.shortcut) {
    const shortcut = document.createElement('span')
    shortcut.style.cssText = 'color: var(--vscode-descriptionForeground, #8a8a8a); margin-left: 20px; font-size: 12px;'
    shortcut.textContent = item.shortcut
    el.appendChild(shortcut)
  }

  if (enabled) {
    el.addEventListener('mouseenter', () => el.style.backgroundColor = 'var(--vscode-menu-selectionBackground, #094771)')
    el.addEventListener('mouseleave', () => el.style.backgroundColor = 'transparent')
    el.addEventListener('click', (e) => { e.stopPropagation(); item.action(); hideContextMenu() })
  }

  return el
}

function showContextMenu(event: MouseEvent): void {
  contextMenuTarget = event.target as HTMLElement

  if (!currentContextMenu) {
    currentContextMenu = createContextMenuElement()
  }
  currentContextMenu.innerHTML = ''

  const editable = isEditableElement(contextMenuTarget) ||
    isEditableElement(contextMenuTarget?.closest('input, textarea, [contenteditable="true"]') as HTMLElement)

  const menuItems: ContextMenuItem[] = [
    {
      id: 'cut', labelKey: 'contextMenu.cut', icon: '✂️', shortcut: 'Ctrl+X',
      action: () => document.execCommand('cut'),
      visible: () => editable, enabled: () => editable && hasSelection()
    },
    {
      id: 'copy', labelKey: 'contextMenu.copy', icon: '📋', shortcut: 'Ctrl+C',
      action: () => {
        const text = getSelectedText()
        if (text) navigator.clipboard.writeText(text).catch(() => document.execCommand('copy'))
      },
      visible: () => true, enabled: () => hasSelection()
    },
    {
      id: 'paste', labelKey: 'contextMenu.paste', icon: '📌', shortcut: 'Ctrl+V',
      action: async () => {
        try {
          const text = await navigator.clipboard.readText()
          if (editable && contextMenuTarget) {
            if (contextMenuTarget.tagName === 'INPUT' || contextMenuTarget.tagName === 'TEXTAREA') {
              const input = contextMenuTarget as HTMLInputElement | HTMLTextAreaElement
              const start = input.selectionStart || 0
              const end = input.selectionEnd || 0
              input.value = input.value.slice(0, start) + text + input.value.slice(end)
              input.selectionStart = input.selectionEnd = start + text.length
              input.dispatchEvent(new Event('input', { bubbles: true }))
            } else {
              document.execCommand('insertText', false, text)
            }
          }
        } catch { document.execCommand('paste') }
      },
      visible: () => editable, enabled: () => editable
    },
    {
      id: 'selectAll', labelKey: 'contextMenu.selectAll', icon: '📄', shortcut: 'Ctrl+A',
      action: () => {
        if (editable && contextMenuTarget) {
          if (contextMenuTarget.tagName === 'INPUT' || contextMenuTarget.tagName === 'TEXTAREA') {
            (contextMenuTarget as HTMLInputElement | HTMLTextAreaElement).select()
          } else {
            document.execCommand('selectAll')
          }
        } else {
          const selection = window.getSelection()
          const range = document.createRange()
          range.selectNodeContents(document.body)
          selection?.removeAllRanges()
          selection?.addRange(range)
        }
      },
      visible: () => true, enabled: () => true
    }
  ]

  const visibleItems = menuItems.filter(item => item.visible())
  if (visibleItems.length === 0) return

  visibleItems.forEach(item => currentContextMenu!.appendChild(createMenuItem(item)))

  const menuWidth = 180, menuHeight = visibleItems.length * 36 + 8
  let x = event.clientX, y = event.clientY

  if (x + menuWidth > window.innerWidth) x = window.innerWidth - menuWidth - 10
  if (y + menuHeight > window.innerHeight) y = window.innerHeight - menuHeight - 10

  currentContextMenu.style.left = `${x}px`
  currentContextMenu.style.top = `${y}px`
  currentContextMenu.style.display = 'block'
}

function hideContextMenu(): void {
  if (currentContextMenu) currentContextMenu.style.display = 'none'
  contextMenuTarget = null
}

function setupContextMenu(): void {
  document.addEventListener('contextmenu', (event: MouseEvent) => {
    event.preventDefault()
    if (currentOptions.showCustomContextMenu) showContextMenu(event)
    if (customContextMenuHandler) customContextMenuHandler(event)
  }, true)
}

// ════════════════════════════════════════════════════════════════════════════
// 销毁
// ════════════════════════════════════════════════════════════════════════════

export function destroyBrowserSecurity(): void {
  initialized = false
  customContextMenuHandler = null
  globalOpenFileCallback = null
  translateFn = null

  if (currentContextMenu) {
    currentContextMenu.remove()
    currentContextMenu = null
  }
}
