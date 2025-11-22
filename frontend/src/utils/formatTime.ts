/**
 * 格式化时间戳
 * - 今天: "14:30"
 * - 昨天: "昨天 14:30"
 * - 更早: "2025-11-05 14:30"
 */
export function formatTime(timestamp: number): string {
  const date = new Date(timestamp)
  const now = new Date()

  // 计算日期差异
  const diffMs = now.getTime() - date.getTime()
  const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24))

  const timeStr = date.toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  })

  if (diffDays === 0) {
    // 今天
    return timeStr
  } else if (diffDays === 1) {
    // 昨天
    return `昨天 ${timeStr}`
  } else {
    // 更早
    const dateStr = date.toLocaleDateString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit'
    }).replace(/\//g, '-')
    return `${dateStr} ${timeStr}`
  }
}

/**
 * 格式化相对时间
 * - 1分钟内: "刚刚"
 * - 1小时内: "5分钟前"
 * - 1天内: "3小时前"
 * - 更早: 使用 formatTime
 */
export function formatRelativeTime(timestamp: number): string {
  const date = new Date(timestamp)
  const now = new Date()

  const diffMs = now.getTime() - date.getTime()
  const diffSeconds = Math.floor(diffMs / 1000)
  const diffMinutes = Math.floor(diffSeconds / 60)
  const diffHours = Math.floor(diffMinutes / 60)

  if (diffSeconds < 60) {
    return '刚刚'
  } else if (diffMinutes < 60) {
    return `${diffMinutes}分钟前`
  } else if (diffHours < 24) {
    return `${diffHours}小时前`
  } else {
    return formatTime(timestamp)
  }
}
