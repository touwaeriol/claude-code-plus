// 消息窗口配置
export const MESSAGE_WINDOW_CORE = 600
export const MESSAGE_WINDOW_RESERVE = 200 // 上下各预留
export const MESSAGE_WINDOW_TOTAL = MESSAGE_WINDOW_CORE + MESSAGE_WINDOW_RESERVE * 2
// 历史分页默认尾部/每页大小，使用窗口总长
export const HISTORY_PAGE_SIZE = MESSAGE_WINDOW_TOTAL

/**
 * 首次加载的消息数量（尾部100条）
 * 设计原则: 足够少以快速加载，足够多以提供初始上下文
 */
export const HISTORY_INITIAL_LOAD = 100

/**
 * 懒加载每次加载的消息数量（50条）
 * 设计原则: 小批量加载，减少单次加载时间
 */
export const HISTORY_LAZY_LOAD_SIZE = 50

/**
 * 自动填满视口时的最大加载量（200条）
 * 设计原则: 避免超宽屏/4K屏幕时无限加载
 */
export const HISTORY_AUTO_LOAD_MAX = 200

/**
 * 懒加载触发阈值（距顶部的距离，单位px）
 * 增大到500px让用户更容易触发
 */
export const HISTORY_TRIGGER_THRESHOLD = 500

/**
 * 懒加载重置阈值（距顶部的距离，单位px）
 * 设置为触发阈值的1.5倍，防止触发后立即重置
 */
export const HISTORY_RESET_THRESHOLD = HISTORY_TRIGGER_THRESHOLD * 1.5
