import localeService from '@/services/localeService'

/**
 * 格式化时间戳
 * - 今天: "14:30"
 * - 昨天: "昨天 14:30" / "Yesterday 14:30"
 * - 更早: "2025-11-05 14:30"
 */
export function formatTime(timestamp: number): string {
  const date = new Date(timestamp)
  const now = new Date()

  // 计算日期差异
  const diffMs = now.getTime() - date.getTime()
  const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24))

  const locale = localeService.getLocale()
  const localeCode = locale === 'zh-CN' ? 'zh-CN' : 'en-US'

  const timeStr = date.toLocaleTimeString(localeCode, {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  })

  if (diffDays === 0) {
    // 今天
    return timeStr
  } else if (diffDays === 1) {
    // 昨天
    const yesterdayText = locale === 'zh-CN' ? '昨天' : 'Yesterday'
    return `${yesterdayText} ${timeStr}`
  } else {
    // 更早
    const dateStr = date.toLocaleDateString(localeCode, {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit'
    }).replace(/\//g, '-')
    return `${dateStr} ${timeStr}`
  }
}

/**
 * 格式化相对时间
 * - 1分钟内: "刚刚" / "Just now"
 * - 1小时内: "5分钟前" / "5 minutes ago"
 * - 1天内: "3小时前" / "3 hours ago"
 * - 更早: 使用 formatTime
 */
export function formatRelativeTime(timestamp: number): string {
  const date = new Date(timestamp)
  const now = new Date()

  const diffMs = now.getTime() - date.getTime()
  const diffSeconds = Math.floor(diffMs / 1000)
  const diffMinutes = Math.floor(diffSeconds / 60)
  const diffHours = Math.floor(diffMinutes / 60)

  const locale = localeService.getLocale()
  const isChinese = locale === 'zh-CN'

  if (diffSeconds < 60) {
    return isChinese ? '刚刚' : 'Just now'
  } else if (diffMinutes < 60) {
    return isChinese 
      ? `${diffMinutes}分钟前` 
      : `${diffMinutes} minute${diffMinutes > 1 ? 's' : ''} ago`
  } else if (diffHours < 24) {
    return isChinese 
      ? `${diffHours}小时前` 
      : `${diffHours} hour${diffHours > 1 ? 's' : ''} ago`
  } else {
    return formatTime(timestamp)
  }
}
