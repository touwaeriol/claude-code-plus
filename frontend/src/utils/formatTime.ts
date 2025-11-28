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
  const localeCodeMap: Record<string, string> = {
    'zh-CN': 'zh-CN',
    'en-US': 'en-US',
    'ko-KR': 'ko-KR',
    'ja-JP': 'ja-JP'
  }
  const localeCode = localeCodeMap[locale] || 'en-US'

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
    const yesterdayTextMap: Record<string, string> = {
      'zh-CN': '昨天',
      'en-US': 'Yesterday',
      'ko-KR': '어제',
      'ja-JP': '昨日'
    }
    const yesterdayText = yesterdayTextMap[locale] || 'Yesterday'
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

  const justNowMap: Record<string, string> = {
    'zh-CN': '刚刚',
    'en-US': 'Just now',
    'ko-KR': '방금',
    'ja-JP': 'たった今'
  }

  const minutesAgoMap: Record<string, (n: number) => string> = {
    'zh-CN': (n) => `${n}分钟前`,
    'en-US': (n) => `${n} minute${n > 1 ? 's' : ''} ago`,
    'ko-KR': (n) => `${n}분 전`,
    'ja-JP': (n) => `${n}分前`
  }

  const hoursAgoMap: Record<string, (n: number) => string> = {
    'zh-CN': (n) => `${n}小时前`,
    'en-US': (n) => `${n} hour${n > 1 ? 's' : ''} ago`,
    'ko-KR': (n) => `${n}시간 전`,
    'ja-JP': (n) => `${n}時間前`
  }

  if (diffSeconds < 60) {
    return justNowMap[locale] || justNowMap['en-US']
  } else if (diffMinutes < 60) {
    const formatter = minutesAgoMap[locale] || minutesAgoMap['en-US']
    return formatter(diffMinutes)
  } else if (diffHours < 24) {
    const formatter = hoursAgoMap[locale] || hoursAgoMap['en-US']
    return formatter(diffHours)
  } else {
    return formatTime(timestamp)
  }
}
