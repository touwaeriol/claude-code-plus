/**
 * JCEF 滚动增强模块
 *
 * 解决 JCEF（JetBrains Chromium Embedded Framework）中滚动不灵敏的问题。
 * 通过拦截 wheel 事件并手动控制滚动量来增强滚动体验。
 *
 * @see https://magpcss.org/ceforum/viewtopic.php?f=17&t=18353
 */

// 保存事件监听器引用，用于清理（防止内存泄漏）
let wheelHandler: ((e: WheelEvent) => void) | null = null
let isInitialized = false

/**
 * 从 URL 参数获取滚动倍增系数
 * @returns 倍增系数，默认为 1（不增强）
 */
export function getScrollMultiplier(): number {
  const params = new URLSearchParams(window.location.search)
  const value = params.get('scrollMultiplier')

  if (value) {
    const num = parseFloat(value)
    if (!isNaN(num) && num > 0) {
      return num
    }
  }

  return 1  // 默认不增强
}

/**
 * 初始化滚动增强
 * 仅当倍增系数 > 1 时才启用
 * @returns 清理函数，调用后移除事件监听器
 */
export function initScrollBoost(): (() => void) | null {
  if (isInitialized) {
    console.log('🖱️ Scroll boost already initialized')
    return cleanupScrollBoost
  }
  
  const multiplier = getScrollMultiplier()

  if (multiplier <= 1) {
    console.log('🖱️ Scroll boost disabled (multiplier <= 1)')
    return null
  }

  console.log(`🖱️ Scroll boost enabled with multiplier: ${multiplier}`)

  wheelHandler = (e: WheelEvent) => {
    // 找到最近的可滚动容器
    let target = e.target as HTMLElement | null

    while (target && target !== document.body) {
      const style = getComputedStyle(target)
      const overflowY = style.overflowY
      const isScrollableY =
        (overflowY === 'auto' || overflowY === 'scroll') &&
        target.scrollHeight > target.clientHeight

      if (isScrollableY) {
        e.preventDefault()
        // 应用倍增系数（减去 1 是因为浏览器会执行默认滚动）
        // 但由于 preventDefault() 阻止了默认行为，所以直接使用完整倍数
        target.scrollTop += e.deltaY * multiplier
        return
      }

      target = target.parentElement
    }

    // 回退到 body 滚动
    if (document.body.scrollHeight > document.body.clientHeight) {
      e.preventDefault()
      document.body.scrollTop += e.deltaY * multiplier
      document.documentElement.scrollTop += e.deltaY * multiplier
    }
  }
  
  document.addEventListener('wheel', wheelHandler, { passive: false })
  isInitialized = true
  
  return cleanupScrollBoost
}

/**
 * 清理滚动增强
 * 移除事件监听器，防止内存泄漏
 */
export function cleanupScrollBoost(): void {
  if (wheelHandler) {
    document.removeEventListener('wheel', wheelHandler)
    wheelHandler = null
    isInitialized = false
    console.log('🖱️ Scroll boost cleanup completed')
  }
}
