/**
 * Tab 会话管理 Composable（核心入口）
 *
 * 每个 Tab 实例独立持有：
 * - 自己的状态
 * - 自己的连接
 * - 自己的消息处理器
 *
 * 组合其他 Composables:
 * - useSessionTools: 工具调用管理
 * - useSessionStats: 统计管理
 * - useSessionPermissions: 权限管理
 * - useSessionMessages: 消息处理
 */

import {ref, reactive, computed, shallowRef, watch} from 'vue'
import {aiAgentService} from '@/services/aiAgentService'
import type {ConnectOptions} from '@/services/aiAgentService'
import {
    RSocketSession,
    createRunToBackgroundError,
    type BashRunToBackgroundResult,
    type RunToBackgroundResult
} from '@/services/rsocket/RSocketSession'
import type {ContentBlock, Message} from '@/types/message'
import {ConnectionStatus} from '@/types/display'
import type {RpcCapabilities, RpcPermissionMode, RpcMessage, RpcStreamEvent, RpcResultMessage} from '@/types/rpc'
import type {BackendType, BackendConfig} from '@/types/backend'
import type {ThinkingConfig} from '@/types/thinking'
import type {BackendSession} from '@/services/backend'
import {BackendSessionFactory} from '@/services/backend'
import {getCapabilities, supportsFeature, type BackendFeature} from '@/services/backendCapabilities'
import {
    isStreamEvent as isRpcStreamEvent,
    isResultMessage as isRpcResultMessage,
    isAssistantMessage as isRpcAssistantMessage,
    isUserMessage as isRpcUserMessage,
    type RpcStatusSystemMessage,
    type RpcCompactBoundaryMessage,
    type RpcCompactMetadata,
    type RpcSystemInitMessage
} from '@/types/rpc'
import {mapRpcMessageToMessage} from '@/utils/rpcMappers'
import {useSessionTools, type SessionToolsInstance} from './useSessionTools'
import {useSessionStats, type SessionStatsInstance} from './useSessionStats'
import {useSessionPermissions, type SessionPermissionsInstance} from './useSessionPermissions'
import {useSessionMessages, type SessionMessagesInstance} from './useSessionMessages'
import {useFileChanges, type FileChangesInstance} from './useFileChanges'
import {useCodexRpcAdapter, type CodexRpcAdapterInstance} from './useCodexRpcAdapter'
import type {ActiveFileInfo} from '@/services/ideaRSocket'
import {loggers} from '@/utils/logger'
import type {PendingPermissionRequest, PendingUserQuestion, PermissionResponse} from '@/types/permission'
import {HISTORY_LAZY_LOAD_SIZE, HISTORY_PAGE_SIZE} from '@/constants/messageWindow'
import {ideaBridge} from '@/services/ideaBridge'
import {useSettingsStore} from '@/stores/settingsStore'

const log = loggers.session

/**
 * 滚动模式
 * - follow: 跟随模式，新消息自动滚动到底部
 * - browse: 浏览模式，锁定位置，显示新消息计数
 */
export type ScrollMode = 'follow' | 'browse'

/**
 * 滚动锚点信息
 * 用于精确恢复滚动位置（基于 item ID 而非像素值）
 */
export interface ScrollAnchor {
    /** 锚点 item 的 ID（稳定，不受历史消息加载影响） */
    itemId: string
    /** 该 item 距离视口顶部的偏移量 (px) */
    offsetFromViewportTop: number
    /** 保存时的视口高度（用于验证） */
    viewportHeight: number
    /** 保存时间戳 */
    savedAt: number
}

/**
 * 滚动状态
 */
export interface ScrollState {
    /** 滚动模式 */
    mode: ScrollMode
    /** 锚点信息（browse 模式下有效） */
    anchor: ScrollAnchor | null
    /** 新消息计数（browse 模式下累计） */
    newMessageCount: number
    /** 是否接近底部 */
    isNearBottom?: boolean
}

/**
 * 默认滚动状态
 */
export const DEFAULT_SCROLL_STATE: ScrollState = {
    mode: 'follow',
    anchor: null,
    newMessageCount: 0
}

/**
 * UI 状态（用于切换会话时保存/恢复）
 */
export interface UIState {
    inputText: string
    contexts: any[]
    autoCleanupContexts: boolean
    /** 滚动状态（替代原来的 scrollPosition/newMessageCount/showScrollToBottom） */
    scrollState: ScrollState
    /** 当前打开的文件是否被禁用（每个 Tab 独立） */
    activeFileDisabled: boolean
}

/**
 * Tab 基础信息
 */
export interface TabInfo {
    tabId: string
    sessionId: string | null
    name: string
    createdAt: number
    updatedAt: number
    lastActiveAt: number
    order: number
}

/**
 * 思考级别（token 数量）
 * - 0: 关闭思考
 * - 正数: 思考 token 预算
 *
 * 常用值：
 * - 0: Off
 * - 1024: Low (1K)
 * - 4096: Medium (4K)
 * - 8192: High (8K) - 默认值
 * - 16384: Very High (16K)
 */
export type ThinkingLevel = number

/**
 * 连接配置
 */
export interface TabConnectOptions {
    backendType?: BackendType
    model?: string
    thinkingLevel?: ThinkingLevel
    permissionMode?: RpcPermissionMode
    skipPermissions?: boolean
    continueConversation?: boolean
    resumeSessionId?: string
    // Codex 特定选项
    reasoningEffort?: string
    sandboxMode?: string
}

/**
 * 设置 key 常量（避免字符串字面量）
 */
export const SETTING_KEYS = {
    MODEL: 'model',
    PERMISSION_MODE: 'permissionMode',
    THINKING_LEVEL: 'thinkingLevel',
    SKIP_PERMISSIONS: 'skipPermissions',
} as const

type PendingSettingKey = 'model' | 'thinkingLevel' | 'permissionMode' | 'skipPermissions'

type PendingSettingValueMap = {
    model: TabConnectOptions['model']
    thinkingLevel: TabConnectOptions['thinkingLevel']
    permissionMode: TabConnectOptions['permissionMode']
    skipPermissions: TabConnectOptions['skipPermissions']
}

/**
 * RPC 消息规范化结果类型
 */
export type NormalizedRpcMessage =
    | { kind: 'message'; data: any }
    | { kind: 'stream_event'; data: RpcStreamEvent }
    | { kind: 'result'; data: RpcResultMessage }
    | { kind: 'status_system'; data: RpcStatusSystemMessage }
    | { kind: 'compact_boundary'; data: RpcCompactBoundaryMessage }
    | { kind: 'system_init'; data: RpcSystemInitMessage }

/**
 * 检查是否是 status_system 消息
 */
function isStatusSystemMessage(msg: RpcMessage): msg is RpcStatusSystemMessage {
    return msg.type === 'status_system'
}

/**
 * 检查是否是 compact_boundary 消息
 */
function isCompactBoundaryMessage(msg: RpcMessage): msg is RpcCompactBoundaryMessage {
    return msg.type === 'compact_boundary'
}

/**
 * 检查是否是 system_init 消息
 */
function isSystemInitMessage(msg: RpcMessage): msg is RpcSystemInitMessage {
    return msg.type === 'system_init'
}

/**
 * Tab 会话管理 Composable
 *
 * 使用方式：
 * ```typescript
 * const tab = useSessionTab()
 * await tab.connect({ model: 'opus' })
 * tab.sendMessage([{ type: 'text', text: 'Hello' }])
 * ```
 */
export function useSessionTab(initialOrder: number = 0) {
    // ========== 组合其他 Composables ==========
    const tools: SessionToolsInstance = useSessionTools()
    const stats: SessionStatsInstance = useSessionStats()
    const permissions: SessionPermissionsInstance = useSessionPermissions()
    const messagesHandler: SessionMessagesInstance = useSessionMessages(tools, stats)
    
    // 文件改动追踪（用于回滚功能）
    // 直接回调机制：由 tools.onToolCompleted 触发
    const fileChanges: FileChangesInstance = useFileChanges(
      computed(() => messagesHandler.displayItems)
    )
    
    // 订阅工具完成事件，直接调用 fileChanges.addFileEdit
    // 这样可以确保在正确的时机（流式处理期间）添加记录
    tools.onToolCompleted((toolCall) => {
      // 只在生成中添加（历史加载时不添加）
      if (messagesHandler.isGenerating.value) {
        fileChanges.addFileEdit(toolCall)
      }
    })

    // ========== Tab 基础信息 ==========
    const tabId = `tab-${Date.now()}-${Math.random().toString(36).substring(2, 8)}`
    /** 永久连接标识，用于 MCP 路由（由后端分配，与 RSocket 连接生命周期绑定） */
    const connectId = ref<string | null>(null)
    const sessionId = ref<string | null>(null)
    const projectPath = ref<string | null>(null)
    const name = ref('新会话')
    const createdAt = Date.now()
    const updatedAt = ref(createdAt)
    const lastActiveAt = ref(createdAt)
    const order = ref(initialOrder)

    // ========== 连接状态 ==========
    // 直接持有 RSocketSession 实例（核心重构：每个 Tab 拥有自己的会话）
    const rsocketSession = shallowRef<RSocketSession | null>(null)

    // 使用 reactive 对象而不是 ref，以便在 shallowRef 容器中也能被追踪
    const connectionState = reactive({
        status: ConnectionStatus.IDLE as ConnectionStatus,
        capabilities: null as RpcCapabilities | null,
        lastError: null as string | null
    })

    // MCP 服务器状态（从 system_init 消息实时更新）
    const mcpServers = ref<Array<{ name: string; status: string }>>([])

    // ========== 多后端支持 ==========
    // 后端类型（默认 Claude）
    const backendType = ref<BackendType>('claude')

    // 后端配置
    const backendConfig = ref<BackendConfig | null>(null)

    // 后端会话实例（新架构：通过 BackendSessionFactory 创建）
    const backendSession = ref<BackendSession | null>(null)

    // ========== Codex RPC 适配器 ==========
    // 将 Codex BackendEvent 转换为 Claude RPC 流式事件格式
    // 延迟初始化，在首次使用时创建（因为需要 messagesHandler 和 tools）
    let codexRpcAdapter: CodexRpcAdapterInstance | null = null
    
    function getCodexRpcAdapter(): CodexRpcAdapterInstance {
        if (!codexRpcAdapter) {
            codexRpcAdapter = useCodexRpcAdapter({
                tabId,
                tools,
                messagesHandler
            })
        }
        return codexRpcAdapter
    }

    // ========== 连接设置（连接时确定，切换需要重连）==========
    const modelId = ref<string | null>(null)
    const thinkingLevel = ref<ThinkingLevel>(8096)  // 默认 Ultra
    const permissionMode = ref<RpcPermissionMode>('default')
    const skipPermissions = ref(false)
    const initialConnectOptions = ref<TabConnectOptions | null>(null)

    // ========== 设置跟踪（用于检测设置变更）==========
    /**
     * 上次应用到后端的设置快照
     */
    const lastAppliedSettings = ref<Partial<TabConnectOptions>>({})

    /**
     * 待应用的设置（在下次发送消息前应用）
     */
    const pendingSettings = ref<Partial<TabConnectOptions>>({})

    /**
     * 更新 lastAppliedSettings 为当前设置
     */
    function updateLastAppliedSettings(): void {
        // skipPermissions 是纯前端行为，不需要跟踪
        lastAppliedSettings.value = {
            model: modelId.value || undefined,
            thinkingLevel: thinkingLevel.value,
            permissionMode: permissionMode.value
        }
    }

    /**
     * 应用待处理的设置（如果有变更）
     */
    async function applyPendingSettingsIfNeeded(): Promise<void> {
        if (Object.keys(pendingSettings.value).length === 0) {
            return
        }

        log.info(`[Tab ${tabId}] 应用待处理设置:`, pendingSettings.value)

        // thinkingLevel 现在可以动态切换，无需重连
        // model 和 permissionMode 也可以 RPC 切换
        if (pendingSettings.value.model !== undefined) {
            await setModel(pendingSettings.value.model)
        }
        if (pendingSettings.value.permissionMode !== undefined) {
            await setPermissionModeValue(pendingSettings.value.permissionMode)
        }
        if (pendingSettings.value.thinkingLevel !== undefined) {
            await setThinkingLevelValue(pendingSettings.value.thinkingLevel)
        }

        // 清空待处理设置
        pendingSettings.value = {}
        updateLastAppliedSettings()
    }

    /**
     * 设置待应用的设置项
     */
    function setPendingSetting<K extends PendingSettingKey>(key: K, value: PendingSettingValueMap[K]): void {
        // skipPermissions 是纯前端行为，不放入 pendingSettings
        if (key === 'skipPermissions') {
            skipPermissions.value = value as boolean
            return
        }

        pendingSettings.value = {...pendingSettings.value, [key]: value}
        // 同时更新本地状态
        switch (key) {
            case 'model':
                modelId.value = value as string
                break
            case 'thinkingLevel':
                thinkingLevel.value = value as ThinkingLevel
                break
            case 'permissionMode':
                permissionMode.value = value as RpcPermissionMode
                break
        }
    }


    function setInitialConnectOptions(options: TabConnectOptions) {
        initialConnectOptions.value = {...options}
        if (options.model) modelId.value = options.model
        if (options.thinkingLevel !== undefined) thinkingLevel.value = options.thinkingLevel
        if (options.permissionMode) permissionMode.value = options.permissionMode
        if (options.skipPermissions !== undefined) skipPermissions.value = options.skipPermissions
    }

    // ========== UI 状态 ==========
    const uiState = reactive<UIState>({
        inputText: '',
        contexts: [],
        autoCleanupContexts: false,
        scrollState: { ...DEFAULT_SCROLL_STATE },
        activeFileDisabled: false
    })

    // ========== 监听消息变化，更新 scrollState.newMessageCount ==========
    // 这确保后台 Tab 在 browse 模式下也能正确统计新消息数量
    let lastDisplayItemsLength = 0
    watch(
        () => messagesHandler.displayItems.length,
        (newLength) => {
            const added = newLength - lastDisplayItemsLength
            lastDisplayItemsLength = newLength

            // 只在 browse 模式下且有新消息时更新计数
            if (added > 0 && uiState.scrollState.mode === 'browse') {
                uiState.scrollState.newMessageCount += added
            }
        },
        { immediate: true }
    )

    // ========== 压缩状态 ==========
    /**
     * 是否正在压缩会话
     */
    const isCompacting = ref(false)

    /**
     * 压缩元数据（压缩完成后保存）
     */
    const compactMetadata = ref<RpcCompactMetadata | null>(null)

    /**
     * 待应用的压缩元数据（用于标记下一条 isReplay=false 的消息为压缩摘要）
     */
    const pendingCompactMetadata = ref<RpcCompactMetadata | null>(null)

    /**
     * 待确认的 /rename 命令目标名称
     * 当发送 /rename xxx 命令时保存 xxx，收到成功响应后应用
     */
    const pendingRenameCommand = ref<string | null>(null)

    /**
     * 恢复来源的会话 ID（如果是从历史 resume 而来）
     */
    const resumeFromSessionId = computed(() => initialConnectOptions.value?.resumeSessionId ?? null)

    // ========== 历史加载状态 ==========
    const historyState = reactive({
        loading: false,
        total: 0,
        loadedStart: 0,
        loadedCount: 0,
        hasMore: false,
        lastOffset: 0,
        lastLimit: HISTORY_PAGE_SIZE
    })

    function resetHistoryState(): void {
        historyState.loading = false
        historyState.total = 0
        historyState.loadedStart = 0
        historyState.loadedCount = 0
        historyState.hasMore = false
        historyState.lastOffset = 0
        historyState.lastLimit = HISTORY_PAGE_SIZE
    }

    function syncHistoryLoadedCount(totalHint: number | null = null): void {
        // ❌ 删除：这个逻辑会错误地重置 loadedStart
        // 当从尾部加载时，loadedStart 已经被 markHistoryRange 正确设置了
        // if (historyState.loadedCount === 0 && messagesHandler.messageCount.value > 0) {
        //   historyState.loadedStart = 0
        // }

        historyState.loadedCount = messagesHandler.messageCount.value
        const rangeEnd = historyState.loadedStart + historyState.loadedCount
        if (totalHint !== null) {
            historyState.total = totalHint
        } else {
            historyState.total = Math.max(historyState.total, rangeEnd)
        }
        historyState.hasMore = historyState.loadedStart > 0 && historyState.total > 0
    }

    function markHistoryRange(offset: number, count: number, totalHint: number | null = null): void {
        const effectiveCount = count ?? 0

        // 处理尾部加载 (offset < 0)
        let actualOffset = offset
        if (offset < 0 && totalHint !== null && effectiveCount > 0) {
            // 从尾部加载时，计算实际的起始位置
            actualOffset = Math.max(0, totalHint - effectiveCount)
        }

        if (historyState.loadedCount === 0 && messagesHandler.messageCount.value === 0) {
            historyState.loadedStart = actualOffset
        } else {
            historyState.loadedStart = historyState.loadedCount === 0
                ? actualOffset
                : Math.min(historyState.loadedStart, actualOffset)
        }
        historyState.lastOffset = offset
        historyState.lastLimit = count || historyState.lastLimit
        // 加载完成后同步总数/区间
        syncHistoryLoadedCount(totalHint)
        if (effectiveCount === 0 && historyState.total === 0 && totalHint !== null) {
            historyState.total = totalHint
        }
    }

    // ========== 计算属性 ==========

    /**
     * 是否已连接
     */
    const isConnected = computed(() => connectionState.status === ConnectionStatus.CONNECTED)

    /**
     * 是否正在连接
     */
    const isConnecting = computed(() => connectionState.status === ConnectionStatus.CONNECTING)

    /**
     * 是否有错误
     */
    const hasError = computed(() => connectionState.status === ConnectionStatus.ERROR)

    /**
     * 是否正在生成
     */
    const isGenerating = computed(() => messagesHandler.isGenerating.value)

    /**
     * Tab 信息
     */
    const tabInfo = computed<TabInfo>(() => ({
        tabId,
        sessionId: sessionId.value,
        name: name.value,
        createdAt,
        updatedAt: updatedAt.value,
        lastActiveAt: lastActiveAt.value,
        order: order.value
    }))

    // ========== 消息规范化 ==========

    /**
     * 规范化 RPC 消息
     */
    function normalizeRpcMessage(raw: RpcMessage): NormalizedRpcMessage | null {
        // 1. 先尝试识别 stream 类型消息（type: "stream_event"）
        // 注意：旧 JSON-RPC 使用 'stream'，RSocket 使用 'stream_event'
        if (isRpcStreamEvent(raw) || (raw as any).type === 'stream') {
            // 检查 stream 事件内部的 data.type 是否是特殊类型
            const innerData = (raw as any).data
            if (innerData) {
                // status_system 消息（压缩状态）嵌套在 stream 里
                if (innerData.type === 'status_system') {
                    log.info('[normalizeRpcMessage] 识别到嵌套的 status_system 消息')
                    return {kind: 'status_system', data: innerData as RpcStatusSystemMessage}
                }
                // compact_boundary 消息（压缩边界）嵌套在 stream 里
                if (innerData.type === 'compact_boundary') {
                    log.info('[normalizeRpcMessage] 识别到嵌套的 compact_boundary 消息')
                    return {kind: 'compact_boundary', data: innerData as RpcCompactBoundaryMessage}
                }
                // user/assistant 消息嵌套在 stream 里
                if (innerData.type === 'user' || innerData.type === 'assistant') {
                    const mapped = mapRpcMessageToMessage(innerData)
                    if (mapped) {
                        return {kind: 'message', data: mapped}
                    }
                }
            }
            // 普通 stream 事件
            return {kind: 'stream_event', data: raw as RpcStreamEvent}
        }

        // 2. 尝试识别 result
        if (isRpcResultMessage(raw)) {
            return {kind: 'result', data: raw}
        }

        // 3. 尝试识别 status_system 消息（压缩状态）- 直接格式
        if (isStatusSystemMessage(raw)) {
            return {kind: 'status_system', data: raw}
        }

        // 4. 尝试识别 compact_boundary 消息（压缩边界）- 直接格式
        if (isCompactBoundaryMessage(raw)) {
            return {kind: 'compact_boundary', data: raw}
        }

        // 5. 尝试识别 system_init 消息
        if (isSystemInitMessage(raw)) {
            log.info('[normalizeRpcMessage] 识别到 system_init 消息, sessionId:', raw.session_id)
            return {kind: 'system_init', data: raw}
        }

        // 6. 尝试识别 assistant / user 消息
        if (isRpcAssistantMessage(raw) || isRpcUserMessage(raw)) {
            const mapped = mapRpcMessageToMessage(raw)
            if (!mapped) return null
            return {kind: 'message', data: mapped}
        }

        log.warn('[normalizeRpcMessage] 未识别的消息类型:', raw.type, raw)
        return null
    }

    /**
     * 处理来自后端的消息
     */
    function handleMessage(rawMessage: RpcMessage): void {
        const normalized = normalizeRpcMessage(rawMessage)
        if (!normalized) return

        // status_system 和 compact_boundary 消息不受生成状态门控，直接处理
        if (normalized.kind === 'status_system') {
            handleStatusSystemMessage(normalized.data)
            touch()
            return
        }

        if (normalized.kind === 'compact_boundary') {
            handleCompactBoundaryMessage(normalized.data)
            touch()
            return
        }

        // system_init 消息用于更新真正的 sessionId
        if (normalized.kind === 'system_init') {
            handleSystemInitMessage(normalized.data)
            touch()
            return
        }

        // Note: 不再根据 isGenerating 状态拦截，收到消息就展示
        // 根据消息类型分发处理
        switch (normalized.kind) {
            case 'stream_event':
                messagesHandler.handleStreamEvent(normalized.data)
                break

            case 'result':
                // 检查是否需要应用 /rename
                if (pendingRenameCommand.value) {
                    const resultData = normalized.data as RpcResultMessage
                    if (resultData.subtype === 'success') {
                        rename(pendingRenameCommand.value)
                        log.info(`[Tab ${tabId}] ✅ /rename 成功，已更新标题: ${pendingRenameCommand.value}`)
                        pendingRenameCommand.value = null
                    } else if (resultData.is_error || resultData.subtype === 'interrupted') {
                        // 失败或中断时清空
                        pendingRenameCommand.value = null
                    }
                }
                messagesHandler.handleResultMessage(normalized.data)
                break

            case 'message':
                // 检查是否需要标记为压缩摘要
                // compact_boundary 后的第一条 isReplay !== true 的 user 消息 = 压缩摘要
                log.info(`[Tab ${tabId}] 处理 message: role=${normalized.data.role}, isReplay=${normalized.data.isReplay}, pendingCompact=${!!pendingCompactMetadata.value}`)
                if (pendingCompactMetadata.value && normalized.data.role === 'user' && normalized.data.isReplay !== true) {
                    normalized.data.isCompactSummary = true
                    normalized.data.compactMetadata = {
                        trigger: pendingCompactMetadata.value.trigger,
                        preTokens: pendingCompactMetadata.value.pre_tokens
                    }
                    log.info(`[Tab ${tabId}] ✅ 标记消息为压缩摘要`, normalized.data.compactMetadata)
                    pendingCompactMetadata.value = null
                }
                // 检测 EnterPlanMode 工具调用，自动切换 UI 模式（在完整消息中检测）
                checkAndHandlePlanModeInMessage(normalized.data)
                messagesHandler.handleNormalMessage(normalized.data)
                break
        }

        if (normalized.kind === 'message') {
            syncHistoryLoadedCount()
        }

        // 更新活跃时间
        touch()
    }

    /**
     * 检测完整消息中的 EnterPlanMode 工具调用，自动切换 UI 权限模式为 plan
     * @param message 完整的消息对象
     */
    function checkAndHandlePlanModeInMessage(message: Message): void {
        // 只检测 assistant 消息
        if (message.role !== 'assistant') {
            return
        }

        // 检查 content 数组中是否包含 EnterPlanMode 工具调用
        const content = message.content
        if (!Array.isArray(content)) {
            return
        }

        for (const block of content) {
            if (block.type === 'tool_use') {
                const toolName = (block as any).toolName || (block as any).name
                if (toolName === 'EnterPlanMode' || toolName === 'enter-plan-mode') {
                    log.info(`[Tab ${tabId}] 检测到 EnterPlanMode（完整消息），自动切换权限模式为 plan`)
                    permissionMode.value = 'plan'
                    return // 找到就退出
                }
            }
        }
    }

    /**
     * 处理 status_system 消息（压缩状态变化）
     */
    function handleStatusSystemMessage(message: RpcStatusSystemMessage): void {
        if (message.status === 'compacting') {
            log.info(`[Tab ${tabId}] 压缩开始`)
            isCompacting.value = true
        } else if (!message.status) {
            // status 为 null/undefined/空字符串 时表示压缩结束
            // 注意：protobuf 的 optional string 解码后，null 可能变成 undefined 或 ""
            log.info(`[Tab ${tabId}] 压缩结束`)
            isCompacting.value = false
        }
    }

    /**
     * 处理 compact_boundary 消息（压缩边界，保存元数据）
     */
    function handleCompactBoundaryMessage(message: RpcCompactBoundaryMessage): void {
        log.info(`[Tab ${tabId}] 📦 收到压缩边界消息`, message.compact_metadata)
        compactMetadata.value = message.compact_metadata || null
        // 保存到 pending，用于标记下一条 user 消息为压缩摘要
        pendingCompactMetadata.value = message.compact_metadata || null
        log.info(`[Tab ${tabId}] 📦 pendingCompactMetadata 已设置:`, pendingCompactMetadata.value)
    }

    /**
     * 处理 system_init 消息（每次 query 开始时从 Claude CLI 发送）
     * 更新 sessionId，用于会话恢复和历史消息关联
     *
     * 新架构说明：每个 Tab 直接持有 RSocketSession 实例，不再通过 Map 查找。
     * 因此可以安全更新 sessionId.value，保持前端与后端 sessionId 同步。
     */
    function handleSystemInitMessage(message: RpcSystemInitMessage): void {
        // 更新 sessionId（新架构下可以安全更新，不再有 Map key 同步问题）
        if (message.session_id && message.session_id !== sessionId.value) {
            log.info(`[Tab ${tabId}] 📦 system_init 更新 sessionId: ${sessionId.value} -> ${message.session_id}`)
            sessionId.value = message.session_id
        }

        // 更新模型信息（如果有变化）
        if (message.model && message.model !== modelId.value) {
            log.info(`[Tab ${tabId}] 📦 system_init 模型: ${message.model}`)
            modelId.value = message.model
        }

        // 更新 MCP 服务器状态（实时显示）
        if (message.mcpServers) {
            mcpServers.value = message.mcpServers.map(s => ({ name: s.name, status: s.status }))
            log.info(`[Tab ${tabId}] 📦 system_init MCP servers: ${mcpServers.value.length}`)
            // 打印完整的 MCP 服务器信息到控制台
            console.log('🔌 MCP Servers:', JSON.stringify(message.mcpServers, null, 2))
        }

        log.debug(`[Tab ${tabId}] 📦 system_init: cwd=${message.cwd}, permissionMode=${message.permissionMode}, tools=${message.tools?.length || 0}`)
    }

    // 连接尝试序号，用于避免旧连接覆盖新状态（例如切换后端时）
    let connectAttemptId = 0
    let pendingConnectSession: RSocketSession | null = null

    function isConnectAttemptStale(attemptId: number, expectedBackend: BackendType): boolean {
        return attemptId !== connectAttemptId || backendType.value !== expectedBackend
    }

    function invalidateConnectAttempt(reason: string): void {
        connectAttemptId += 1
        if (pendingConnectSession) {
            try {
                pendingConnectSession.disconnect()
            } catch (error) {
                log.debug(`[Tab ${tabId}] 断开待连接会话失败: ${error}`)
            } finally {
                pendingConnectSession = null
            }
        }
        log.debug(`[Tab ${tabId}] 连接尝试已失效: ${reason}`)
        cancelReconnect()
    }

    // ========== 连接管理 ==========

    // 重连配置
    const MAX_RECONNECT_ATTEMPTS = 3
    const RECONNECT_DELAY = 2000 // 2秒
    let reconnectAttempts = 0
    let reconnectTimer: ReturnType<typeof setTimeout> | null = null

    /**
     * 处理会话被动断开
     */
    function handleSessionDisconnected(error?: Error): void {
        log.warn(`[Tab ${tabId}] 会话被动断开`, error?.message)

        // ✅ 保留 sessionId 用于重连恢复会话上下文（不清空）
        const currentSessionIdForResume = sessionId.value

        // 更新连接状态为 RECONNECTING（准备自动重连）
        connectionState.status = ConnectionStatus.RECONNECTING
        connectionState.lastError = error?.message || '连接已断开'
        rsocketSession.value = null
        // 注意：不清空 sessionId，保留用于重连

        // 停止生成状态
        if (messagesHandler.isGenerating.value) {
            messagesHandler.stopGenerating()
        }

        // 取消所有待处理的权限和问题
        permissions.cancelAllPermissions('连接断开')
        permissions.cancelAllQuestions('连接断开')

        // 显示错误提示消息并触发自动重连
        messagesHandler.addErrorMessage('连接已断开，正在自动重连，请稍后重新发送消息')

        // ✅ 更新 initialConnectOptions，加入 resumeSessionId 参数
        // 这样即使用户在自动重连前手动发送消息（触发 ensureConnected），也能正确恢复会话
        if (initialConnectOptions.value) {
            const resumeId = currentSessionIdForResume || initialConnectOptions.value.resumeSessionId
            initialConnectOptions.value = {
                ...initialConnectOptions.value,
                resumeSessionId: resumeId
            }
            log.info(`[Tab ${tabId}] 已更新 initialConnectOptions.resumeSessionId=${resumeId}`)
        }

        // 触发自动重连，携带当前会话 ID 以恢复会话上下文
        if (initialConnectOptions.value) {
            log.info(`[Tab ${tabId}] 触发自动重连，resumeSessionId=${initialConnectOptions.value.resumeSessionId}`)
            scheduleReconnect(initialConnectOptions.value)
        }
    }

    /**
     * 连接到后端
     * 
     * 会话 ID 统一使用 sessionId.value：
     * - 新会话：sessionId.value 为空
     * - 历史会话恢复：先设置 sessionId.value，再调用 connect
     * - 断开重连：sessionId.value 已有值
     */
    async function connect(options: TabConnectOptions = {}): Promise<void> {
        const resolvedOptions: TabConnectOptions = {...(initialConnectOptions.value || {}), ...options}
        if (connectionState.status === ConnectionStatus.CONNECTING ||
            connectionState.status === ConnectionStatus.AUTHENTICATING) {
            log.warn(`[Tab ${tabId}] 正在连接中（${connectionState.status}），请勿重复连接`)
            return
        }

        const expectedBackend = backendType.value
        const attemptId = ++connectAttemptId

        if (pendingConnectSession) {
            try {
                pendingConnectSession.disconnect()
            } catch (error) {
                log.debug(`[Tab ${tabId}] 断开待连接会话失败: ${error}`)
            } finally {
                pendingConnectSession = null
            }
        }

        if (backendSession.value) {
            log.info(`[Tab ${tabId}] 断开 BackendSession 连接`)
            backendSession.value.disconnect()
            backendSession.value = null
            backendConfig.value = null
        }

        // 保存当前 sessionId，用于重连时恢复会话（在清空前保存）
        const resumeId = sessionId.value

        // 如果已有连接，先断开旧连接
        if (rsocketSession.value) {
            log.info(`[Tab ${tabId}] 断开旧连接: ${sessionId.value}`)
            rsocketSession.value.disconnect()
            rsocketSession.value = null
            sessionId.value = null
        }

        connectionState.status = ConnectionStatus.CONNECTING
        connectionState.lastError = null

        const session = new RSocketSession()
        pendingConnectSession = session

        try {
            // 订阅消息事件（避免旧连接回写状态）
            session.onMessage((message) => {
                if (isConnectAttemptStale(attemptId, expectedBackend)) {
                    return
                }
                handleMessage(message)
            })

            // 订阅断开事件（被动断开时触发）
            session.onDisconnect((error) => {
                if (rsocketSession.value === session) {
                    handleSessionDisconnected(error)
                }
            })

            // 从 settingsStore 获取设置（已从 IDEA 同步）
            const settingsStore = useSettingsStore()
            const connectOptions: ConnectOptions = {
                includePartialMessages: settingsStore.settings.includePartialMessages ?? true,
                allowDangerouslySkipPermissions: true,
                model: modelId.value || undefined,
                // 连接时只传递 boolean（是否启用思考），具体级别在连接后设置
                thinkingEnabled: thinkingLevel.value > 0,
                permissionMode: permissionMode.value,
                // 始终为 false，让权限检查走前端 RequestPermission 处理器
                // 这样前端的 skipPermissions 动态切换才能生效
                dangerouslySkipPermissions: false,
                // 统一使用 sessionId.value 作为恢复会话的 ID
                continueConversation: !!resumeId,
                resumeSessionId: resumeId || undefined,
                // 固定开启重放用户消息
                replayUserMessages: true,
                // 统一协议：传递 provider 参数，后端根据此参数路由到对应的 AI Agent
                provider: (resolvedOptions as any).provider || backendType.value
                // connectId 现在由后端分配，不再前端传递
            }
            if (backendType.value === 'codex') {
                if (settingsStore.settings.codexSandboxMode) {
                    connectOptions.sandboxMode = settingsStore.settings.codexSandboxMode as any
                }
                if (settingsStore.settings.codexReasoningEffort) {
                    connectOptions.codexReasoningEffort = settingsStore.settings.codexReasoningEffort
                }
                if (settingsStore.settings.codexReasoningSummary) {
                    connectOptions.codexReasoningSummary = settingsStore.settings.codexReasoningSummary
                }
            }

            // RSocket 建立完成，进入认证阶段
            connectionState.status = ConnectionStatus.AUTHENTICATING

            // 连接并获取 sessionId（包含 agent.connect RPC）
            const newSessionId = await session.connect(connectOptions)

            if (isConnectAttemptStale(attemptId, expectedBackend)) {
                log.info(`[Tab ${tabId}] 连接结果已过期，忽略 (provider=${expectedBackend})`)
                session.disconnect()
                return
            }

            // 记录分片消息模式，用于 handleNormalMessage 判断是否忽略完整消息
            messagesHandler.setIncludePartialMessages(connectOptions.includePartialMessages ?? true)

            // 连接成功后，设置具体的思考级别（如果启用了思考且 provider 支持）
            if (thinkingLevel.value > 0 && session.capabilities?.canThink) {
                try {
                    await session.setMaxThinkingTokens(thinkingLevel.value)
                    log.info(`[Tab ${tabId}] 思考级别已设置: ${thinkingLevel.value}`)
                } catch (e) {
                    // 静默处理，不输出警告
                }
            }

            // 保存会话实例和状态
            rsocketSession.value = session
            sessionId.value = newSessionId
            // 保存后端分配的 connectId（用于 MCP 路由）
            connectId.value = session.connectId
            connectionState.capabilities = session.capabilities
            connectionState.status = ConnectionStatus.CONNECTED
            connectionState.lastError = null

            // 连接成功，重置重连计数
            reconnectAttempts = 0

            // 设置处理队列前的回调（用于应用 pending settings）
            messagesHandler.setBeforeProcessQueueFn(async () => {
                log.debug(`[Tab ${tabId}] 处理队列前，应用 pending settings`)
                await applyPendingSettingsIfNeeded()
            })

            // 设置处理队列消息的回调（用于自动发送队列中的下一条消息）
            messagesHandler.setProcessQueueFn(async () => {
                log.debug(`[Tab ${tabId}] 处理队列中的下一条消息`)
                await processNextQueuedMessage()
            })

            // 注册双向 RPC 处理器
            registerRpcHandlers()

            // 连接成功后，更新 lastAppliedSettings 并清空 pendingSettings
            updateLastAppliedSettings()
            pendingSettings.value = {}

            // 获取并保存项目路径
            try {
                const pathResult = await ideaBridge.query('ide.getProjectPath', {})
                if (pathResult.success && pathResult.data?.projectPath) {
                    projectPath.value = pathResult.data.projectPath as string
                    log.info(`[Tab ${tabId}] 项目路径: ${projectPath.value}`)
                }
            } catch (e) {
                log.warn(`[Tab ${tabId}] 获取项目路径失败:`, e)
            }

            log.info(`[Tab ${tabId}] 连接成功: sessionId=${newSessionId}`)

            // 场景区分（基于 resumeId，在清空 sessionId.value 前已保存）：
            // 1. 新会话：resumeId 为空 → 不加载历史
            // 2. 历史会话恢复：resumeId 有值 且 displayItems 为空 → 加载历史
            // 3. 断开重连：resumeId 有值 且 displayItems 不为空 → 不加载历史（消息还在内存）
            if (resumeId && messagesHandler.displayItems.length === 0) {
                log.info(`[Tab ${tabId}] 历史会话恢复，加载历史记录 (resumeId=${resumeId})`)
                try {
                    await loadHistory({
                        sessionId: newSessionId,
                        projectPath: projectPath.value ?? undefined
                    })
                    log.info(`[Tab ${tabId}] 历史记录加载完成`)
                } catch (e) {
                    log.warn(`[Tab ${tabId}] 历史记录加载失败:`, e)
                }
            } else if (resumeId) {
                log.info(`[Tab ${tabId}] 断开重连，跳过历史加载`)
            }

            // 连接成功后，处理队列中的消息
            processNextQueuedMessage()
        } catch (error) {
            if (isConnectAttemptStale(attemptId, expectedBackend)) {
                log.debug(`[Tab ${tabId}] 连接失败但已过期，忽略`)
                return
            }
            connectionState.status = ConnectionStatus.ERROR
            connectionState.lastError = error instanceof Error ? error.message : String(error)
            log.error(`[Tab ${tabId}] 连接失败:`, error)

            // 自动重连
            scheduleReconnect(options, attemptId, expectedBackend)
        } finally {
            if (pendingConnectSession === session) {
                pendingConnectSession = null
            }
        }
    }

    /**
     * 安排自动重连
     */
    function scheduleReconnect(
        options: TabConnectOptions,
        attemptId: number = connectAttemptId,
        expectedBackend: BackendType = backendType.value
    ): void {
        if (isConnectAttemptStale(attemptId, expectedBackend)) {
            log.debug(`[Tab ${tabId}] 重连已过期，忽略`)
            return
        }

        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            log.warn(`[Tab ${tabId}] 已达到最大重连次数 (${MAX_RECONNECT_ATTEMPTS})，停止重连`)
            return
        }

        if (reconnectTimer) {
            clearTimeout(reconnectTimer)
        }

        reconnectAttempts++
        const delay = RECONNECT_DELAY * reconnectAttempts // 逐渐增加延迟

        log.info(`[Tab ${tabId}] 将在 ${delay}ms 后尝试第 ${reconnectAttempts} 次重连`)

        const scheduledAttemptId = attemptId
        const scheduledBackend = expectedBackend
        reconnectTimer = setTimeout(async () => {
            reconnectTimer = null
            if (isConnectAttemptStale(scheduledAttemptId, scheduledBackend)) {
                log.debug(`[Tab ${tabId}] 重连已过期，跳过`)
                return
            }
            connectionState.status = ConnectionStatus.DISCONNECTED // 重置状态以允许重连
            await connect(options)
        }, delay)
    }

    /**
     * 取消自动重连
     */
    function cancelReconnect(): void {
        if (reconnectTimer) {
            clearTimeout(reconnectTimer)
            reconnectTimer = null
        }
        reconnectAttempts = 0
    }

    /**
     * 主动断开连接
     */
    async function disconnect(): Promise<void> {
        invalidateConnectAttempt('disconnect')

        if (rsocketSession.value) {
            rsocketSession.value.disconnect()
            rsocketSession.value = null
        }

        if (backendSession.value) {
            backendSession.value.disconnect()
            backendSession.value = null
            backendConfig.value = null
        }

        sessionId.value = null
        connectionState.status = ConnectionStatus.DISCONNECTED

        // 取消所有待处理的权限和问题
        permissions.cancelAllPermissions('Tab disconnected')
        permissions.cancelAllQuestions('Tab disconnected')

        log.info(`[Tab ${tabId}] 已断开连接`)
    }

    /**
     * 销毁会话（完全清理所有资源）
     *
     * 在前端删除 Tab 时调用，执行完整清理：
     * 1. 断开 Claude CLI 客户端
     * 2. 注销 MCP HTTP Gateway 端点
     * 3. 释放 Terminal MCP 会话资源
     *
     * 注意：disconnect() 仅断开客户端，不清理 MCP 资源，支持重连。
     * disposeSession() 执行完整清理，Tab 删除时调用。
     */
    async function disposeSession(): Promise<void> {
        // 取消自动重连
        cancelReconnect()

        if (rsocketSession.value) {
            try {
                await rsocketSession.value.disposeSession()
            } catch (err) {
                log.warn(`[Tab ${tabId}] disposeSession RPC 失败:`, err)
            }
            rsocketSession.value = null
        }

        sessionId.value = null
        connectionState.status = ConnectionStatus.DISCONNECTED

        // 取消所有待处理的权限和问题
        permissions.cancelAllPermissions('Tab disposed')
        permissions.cancelAllQuestions('Tab disposed')

        log.info(`[Tab ${tabId}] 会话已销毁`)
    }

    /**
     * 流式加载历史记录（用于回放）
     */
    async function loadHistory(
        params: { sessionId?: string; projectPath?: string; offset?: number; limit?: number },
        options?: { mode?: 'append' | 'prepend'; __skipProbe?: boolean }
    ): Promise<void> {
        if (historyState.loading) return

        // 首次加载且未指定 offset/limit 时，先探测总数，优先拉取尾部一页
        if (
            !options?.__skipProbe &&
            historyState.loadedCount === 0 &&
            params.offset === undefined &&
            params.limit === undefined &&
            options?.mode === undefined
        ) {
            const total = await probeHistoryTotal(params)
            const tailOffset = total !== null && total > HISTORY_PAGE_SIZE
                ? total - HISTORY_PAGE_SIZE
                : 0
            return loadHistory(
                {...params, offset: tailOffset, limit: HISTORY_PAGE_SIZE},
                {mode: 'append', __skipProbe: true}
            )
        }

        const offset = params.offset ?? historyState.lastOffset ?? 0
        const limit = params.limit ?? HISTORY_PAGE_SIZE
        const insertMode = options?.mode ?? 'append'

        historyState.loading = true

        try {
            // 调用非流式 API，一次性获取结果
            const result = await aiAgentService.loadHistory(
                {...params, offset, limit},
                backendType.value
            )

            log.info(`[Tab ${tabId}] 📜 历史加载完成: offset=${offset}, count=${result.count}, availableCount=${result.availableCount}, mode=${insertMode}`)

            // 将 RpcMessage 转换为 Message
            const buffer: Message[] = []
            for (const rawMsg of result.messages) {
                const normalized = normalizeRpcMessage(rawMsg)
                if (normalized && normalized.kind === 'message') {
                    buffer.push(normalized.data)
                } else if (normalized && normalized.kind === 'stream_event') {
                    // 流式事件也需要处理（如有必要）
                    // 暂时跳过
                }
            }

            if (buffer.length > 0) {
                log.info(`[Tab ${tabId}] 📜 准备插入 ${buffer.length} 条消息到 UI (${insertMode})`)

                if (insertMode === 'prepend') {
                    messagesHandler.prependMessagesBatch(buffer)
                } else {
                    messagesHandler.appendMessagesBatch(buffer)
                }

                log.info(`[Tab ${tabId}] 📜 ✅ ${buffer.length} 条消息已成功添加到 displayItems`)
            } else {
                log.warn(`[Tab ${tabId}] 📜 ⚠️ 缓冲区为空，没有消息可加载`)
            }

            // 更新历史状态
            markHistoryRange(offset, buffer.length, result.availableCount)
        } catch (error) {
            log.error(`[Tab ${tabId}] 历史加载失败:`, error)
            throw error
        } finally {
            historyState.loading = false
        }
    }

    /**
     * 探测历史总数（通过 getHistoryMetadata API）
     */
    async function probeHistoryTotal(params: { sessionId?: string; projectPath?: string }): Promise<number | null> {
        try {
            const metadata = await aiAgentService.getHistoryMetadata(params, backendType.value)
            return metadata.totalLines
        } catch (error) {
            log.warn(`[Tab ${tabId}] 获取历史元数据失败:`, error)
            return null
        }
    }

    /**
     * 顶部分页加载更早的历史
     */
    async function loadMoreHistory(): Promise<void> {
        if (historyState.loading) return
        if (!historyState.hasMore) return

        const nextOffset = Math.max(0, historyState.loadedStart - HISTORY_LAZY_LOAD_SIZE)
        const nextLimit = historyState.loadedStart - nextOffset || HISTORY_LAZY_LOAD_SIZE

        await loadHistory(
            {
                sessionId: sessionId.value ?? undefined,
                offset: nextOffset,
                limit: nextLimit
            },
            {mode: 'prepend'}
        )
    }

    /**
     * 重新连接（复用 WebSocket）
     * 只发送 disconnect + connect RPC，不关闭 WebSocket
     */
    async function reconnect(options?: TabConnectOptions): Promise<void> {
        if (!rsocketSession.value) {
            // 如果没有会话，走完整的 connect 流程
            await connect(options || {
                model: modelId.value || undefined,
                thinkingLevel: thinkingLevel.value,
                permissionMode: permissionMode.value,
                skipPermissions: skipPermissions.value
            })
            return
        }

        connectionState.status = ConnectionStatus.CONNECTING
        connectionState.lastError = null

        // 更新本地设置
        if (options?.model) modelId.value = options.model
        if (options?.thinkingLevel !== undefined) thinkingLevel.value = options.thinkingLevel
        if (options?.permissionMode) permissionMode.value = options.permissionMode
        if (options?.skipPermissions !== undefined) skipPermissions.value = options.skipPermissions

        try {
            // 从 settingsStore 获取设置（已从 IDEA 同步）
            const settingsStore = useSettingsStore()
            const connectOptions: ConnectOptions = {
                includePartialMessages: settingsStore.settings.includePartialMessages ?? true,
                allowDangerouslySkipPermissions: true,
                model: modelId.value || undefined,
                // 连接时只传递 boolean（是否启用思考），具体级别在连接后设置
                thinkingEnabled: thinkingLevel.value > 0,
                permissionMode: permissionMode.value,
                // 始终为 false，让权限检查走前端 RequestPermission 处理器
                // 这样前端的 skipPermissions 动态切换才能生效
                dangerouslySkipPermissions: false,
                continueConversation: options?.continueConversation,
                resumeSessionId: options?.resumeSessionId,
                // 统一协议：传递 provider 参数
                provider: backendType.value
            }
            if (backendType.value === 'codex') {
                if (settingsStore.settings.codexSandboxMode) {
                    connectOptions.sandboxMode = settingsStore.settings.codexSandboxMode as any
                }
                if (settingsStore.settings.codexReasoningEffort) {
                    connectOptions.codexReasoningEffort = settingsStore.settings.codexReasoningEffort
                }
                if (settingsStore.settings.codexReasoningSummary) {
                    connectOptions.codexReasoningSummary = settingsStore.settings.codexReasoningSummary
                }
            }

            // 使用 reconnectSession 复用 WebSocket
            const newSessionId = await rsocketSession.value.reconnectSession(connectOptions)

            // 重连成功后，设置具体的思考级别（如果启用了思考且 provider 支持）
            if (thinkingLevel.value > 0 && rsocketSession.value.capabilities?.canThink) {
                try {
                    await rsocketSession.value.setMaxThinkingTokens(thinkingLevel.value)
                    log.info(`[Tab ${tabId}] 重连后思考级别已设置: ${thinkingLevel.value}`)
                } catch (e) {
                    // 静默处理，不输出警告
                }
            }

            sessionId.value = newSessionId
            connectionState.capabilities = rsocketSession.value.capabilities
            connectionState.status = ConnectionStatus.CONNECTED
            connectionState.lastError = null

            // 连接成功后，更新 lastAppliedSettings 并清空 pendingSettings
            updateLastAppliedSettings()
            pendingSettings.value = {}

            log.info(`[Tab ${tabId}] 重连成功: sessionId=${newSessionId}`)

            // 重连成功后，处理队列中的消息
            processNextQueuedMessage()
        } catch (error) {
            connectionState.status = ConnectionStatus.ERROR
            connectionState.lastError = error instanceof Error ? error.message : String(error)
            log.error(`[Tab ${tabId}] 重连失败:`, error)

            // 显示错误提示
            messagesHandler.addErrorMessage(`连接失败: ${connectionState.lastError}`)
        }
    }

    /**
     * 重载会话（保留 sessionId，重置 UI，恢复历史）
     *
     * 用于"重载会话"按钮：
     * 1. 保存当前 sessionId
     * 2. 重置 Tab（清空消息/显示）
     * 3. 使用原 sessionId 重连（continueConversation + resumeSessionId）
     * 4. 加载该会话的历史记录
     */
    async function reloadSession(): Promise<void> {
        const currentSessionId = sessionId.value
        const currentProjectPath = projectPath.value

        if (!currentSessionId) {
            log.warn(`[Tab ${tabId}] 无法重载：没有当前会话 ID`)
            return
        }

        log.info(`[Tab ${tabId}] 开始重载会话: ${currentSessionId}`)

        // 1. 重置 Tab（清空消息/显示）
        reset()

        // 2. 断开当前连接（如果有）
        if (rsocketSession.value) {
            try {
                rsocketSession.value.disconnect()
            } catch (e) {
                // 忽略断开错误
            }
        }

        // 3. 使用原 sessionId 重连
        connectionState.status = ConnectionStatus.CONNECTING
        connectionState.lastError = null

        try {
            await reconnect({
                continueConversation: true,
                resumeSessionId: currentSessionId
            })

            // 4. 加载历史记录
            await loadHistory({
                sessionId: currentSessionId,
                projectPath: currentProjectPath ?? undefined,
                offset: -1,  // 从尾部加载
                limit: HISTORY_PAGE_SIZE
            })

            log.info(`[Tab ${tabId}] 会话重载完成: ${currentSessionId}`)
        } catch (error) {
            connectionState.status = ConnectionStatus.ERROR
            connectionState.lastError = error instanceof Error ? error.message : String(error)
            log.error(`[Tab ${tabId}] 会话重载失败:`, error)
            messagesHandler.addErrorMessage(`重载失败: ${connectionState.lastError}`)
        }
    }

    // ========== RPC 处理器注册 ==========

    /**
     * 注册双向 RPC 处理器
     */
    function registerRpcHandlers(): void {
        if (!rsocketSession.value) return

        // 注册 AskUserQuestion 处理器
        // Protobuf 模式：参数已经是解码后的 AskUserQuestionParams 对象
        rsocketSession.value.register('AskUserQuestion', async (params: Record<string, any>) => {
            log.info(`[Tab ${tabId}] 收到 AskUserQuestion 请求: ${params.questions?.length || 0} 个问题`)

            return new Promise((resolve, reject) => {
                const questionId = `question-${Date.now()}`

                const question: Omit<PendingUserQuestion, 'createdAt'> = {
                    id: questionId,
                    sessionId: sessionId.value!,
                    questions: params.questions || [],
                    resolve: (answers) => {
                        // 返回 UserAnswerItem[] 格式（Protobuf encodeServerCallResponse 需要）
                        resolve(answers)
                    },
                    reject
                }

                permissions.addUserQuestion(question)
            })
        })

        // 注册 RequestPermission 处理器
        // Protobuf 模式：参数已经是解码后的 RequestPermissionParams 对象
        rsocketSession.value.register('RequestPermission', async (params: Record<string, any>) => {
            const toolName = params.toolName || 'Unknown'
            const toolUseId = params.toolUseId
            const input = params.input || {}
            const permissionSuggestions = params.permissionSuggestions

            log.info(`[Tab ${tabId}] 收到权限请求: ${toolName}`)

            // skipPermissions (Bypass) 模式下自动批准（ExitPlanMode 除外，必须用户确认）
            if (skipPermissions.value && toolName !== 'ExitPlanMode') {
                log.info(`[Tab ${tabId}] Bypass 模式，自动批准: ${toolName}`)
                return { approved: true }
            }

            return new Promise((resolve, reject) => {
                const permissionId = `permission-${Date.now()}`

                const request: Omit<PendingPermissionRequest, 'createdAt'> = {
                    id: permissionId,
                    sessionId: sessionId.value!,
                    toolName,
                    input,
                    matchedToolCallId: toolUseId,
                    permissionSuggestions,
                    resolve: (response: PermissionResponse) => {
                        // 返回 PermissionResponse 格式（Protobuf encodeServerCallResponse 需要）
                        resolve(response)
                    },
                    reject
                }

                permissions.addPermissionRequest(request)
            })
        })

        log.debug(`[Tab ${tabId}] RPC 处理器已注册`)
    }

    // ========== 消息发送 ==========

    /**
     * 确保连接就绪
     * - 如果已连接，直接返回
     * - 如果正在连接，等待连接完成
     * - 如果断开，触发重连
     *
     * 统一使用 RSocket 连接，通过 provider 参数区分后端类型
     */
    async function ensureConnected(): Promise<void> {
        if (connectionState.status === ConnectionStatus.CONNECTED) {
            log.debug(`[Tab ${tabId}] 连接已就绪，无需重连`)
            return
        }

        // 正在连接中（CONNECTING 或 AUTHENTICATING）或正在重连中（RECONNECTING），等待完成
        if (connectionState.status === ConnectionStatus.CONNECTING ||
            connectionState.status === ConnectionStatus.AUTHENTICATING ||
            connectionState.status === ConnectionStatus.RECONNECTING) {
            log.info(`[Tab ${tabId}] 正在连接中（${connectionState.status}），等待连接完成...`)
            await new Promise<void>((resolve, reject) => {
                const check = () => {
                    if (connectionState.status === ConnectionStatus.CONNECTED) {
                        resolve()
                    } else if (connectionState.status === ConnectionStatus.ERROR ||
                        connectionState.status === ConnectionStatus.DISCONNECTED) {
                        reject(new Error(connectionState.lastError || '连接失败'))
                    } else {
                        setTimeout(check, 100)
                    }
                }
                check()
            })
            return
        }

        log.info(`[Tab ${tabId}] 连接未建立（${connectionState.status}），开始连接 (provider=${backendType.value})...`)

        // 统一使用 RSocket 连接，通过 provider 参数区分后端
        const options = {
            ...(initialConnectOptions.value || {}),
            provider: backendType.value  // 关键：传递 provider 参数
        }
        await connect(options)
    }

    /**
     * 发送消息
     * - 生成中：只加入队列（不显示到 UI）
     * - 非生成中：显示到 UI → 应用设置 → 确保连接 → 发送
     *
     * @param message - 消息内容
     * @param options - 发送选项
     * @param options.isSlashCommand - 是否是斜杠命令（斜杠命令不发送 contexts）
     */
    async function sendMessage(
        message: { contexts: any[]; contents: ContentBlock[]; ideContext?: ActiveFileInfo | null },
        options?: { isSlashCommand?: boolean }
    ): Promise<void> {
        // 如果是斜杠命令，清空 contexts 和 ideContext
        if (options?.isSlashCommand) {
            log.info(`[Tab ${tabId}] 检测到斜杠命令，忽略 contexts 和 ideContext`)
            message = {...message, contexts: [], ideContext: null}
        }
        // 检测 /rename 命令
        const textContent = message.contents.find(c => c.type === 'text') as { text?: string } | undefined
        if (textContent?.text) {
            const renameMatch = textContent.text.match(/^\/rename\s+(.+)$/)
            if (renameMatch) {
                pendingRenameCommand.value = renameMatch[1].trim()
                log.info(`[Tab ${tabId}] 检测到 /rename 命令，待确认名称: ${pendingRenameCommand.value}`)
            }
        }

        // 自动更新 Tab 名称：第一条消息时，用消息内容作为标题
        if (messagesHandler.messages.length === 0 && textContent?.text) {
            const text = textContent.text.trim()
            // 跳过命令（以 / 开头）
            if (!text.startsWith('/')) {
                // 截取前 30 个字符，超出部分用 ... 表示
                const maxLen = 30
                const newTitle = text.length > maxLen ? text.slice(0, maxLen) + '...' : text
                rename(newTitle)
                log.info(`[Tab ${tabId}] 自动设置标题: ${newTitle}`)
            }
        }

        // 连接未就绪：先入队，等待连接后处理
        if (connectionState.status !== ConnectionStatus.CONNECTED) {
            log.info(`[Tab ${tabId}] 连接未就绪（${connectionState.status}），消息入待办队列`)
            messagesHandler.addToQueue(message)
            // 若当前不在连接中，则主动触发连接；连接成功后会在 connect/reconnect 的回调里处理队列
            if (connectionState.status !== ConnectionStatus.CONNECTING) {
                await ensureConnected()
            }
            return
        }

        // ★ 如果正在生成中，只加入队列（不添加到 UI）
        if (messagesHandler.isGenerating.value) {
            log.info(`[Tab ${tabId}] 正在生成中，消息只加入队列`)
            messagesHandler.addToQueue(message)
            return
        }

        // ★ 没有生成中：添加到 UI → 应用设置 → 确保连接 → 发送
        log.info(`[Tab ${tabId}] 消息不在队列中，直接处理`)
        const {userMessage, mergedContent} = messagesHandler.addMessageToUI(message)
        touch()

        // 发送消息到后端
        await sendMessageToBackend(userMessage, mergedContent, message)
    }

    /**
     * 发送消息到后端（内部方法）
     *
     * 统一使用 RSocket 协议，通过 provider 参数区分后端类型
     * 后端会根据 provider 路由到正确的 AI Agent (Claude 或 Codex)
     */
    async function sendMessageToBackend(
        userMessage: Message,
        mergedContent: ContentBlock[],
        originalMessage: { contexts: any[]; contents: ContentBlock[] }
    ): Promise<void> {
        try {
            // 确保连接就绪（统一使用 RSocket，connect 时已传递 provider 参数）
            log.info(`[Tab ${tabId}] 确保连接就绪 (provider=${backendType.value})...`)
            await ensureConnected()

            // 设置生成状态
            messagesHandler.startGenerating(userMessage.id)
            log.info(`[Tab ${tabId}] 开始发送消息到后端 (provider=${backendType.value})...`)

            // 统一使用 RSocketSession 发送消息
            if (!rsocketSession.value) {
                throw new Error('会话未连接')
            }

            await rsocketSession.value.sendMessageWithContent(mergedContent as any)
            log.info(`[Tab ${tabId}] 消息发送完成 (provider=${backendType.value})`)
        } catch (err) {
            log.error(`[Tab ${tabId}] ❌ 发送消息失败:`, err)
            // 停止生成状态
            messagesHandler.stopGenerating()
            // 消息已在 UI 中，加入队列等待重试
            messagesHandler.addToQueue(originalMessage)
        }
    }

    /**
     * 处理队列中的下一条消息
     */
    async function processNextQueuedMessage(): Promise<void> {
        const next = messagesHandler.popNextQueuedMessage()
        if (!next) {
            return
        }

        log.info(`[Tab ${tabId}] 处理队列消息: ${next.userMessage.id}`)
        await sendMessageToBackend(next.userMessage, next.mergedContent, next.originalMessage)
    }

    /**
     * 发送纯文本消息
     */
    async function sendTextMessage(text: string): Promise<void> {
        await sendMessage({
            contexts: [],
            contents: [{type: 'text', text}]
        })
    }

    /**
     * 直接发送文本消息到后端（绕过队列和 UI 显示）
     *
     * 用于外部组件（如 ChatHeader、ideSessionBridge）发送斜杠命令（如 /rename）
     * 不会将消息添加到 UI，不受队列和生成状态影响
     *
     * @param text - 要发送的纯文本消息
     */
    async function sendTextMessageDirect(text: string): Promise<void> {
        if (!rsocketSession.value) {
            throw new Error('会话未连接')
        }
        await rsocketSession.value.sendMessage(text)
        log.info(`[Tab ${tabId}] 直接发送文本消息: ${text}`)
    }

    /**
     * 中断当前操作
     *
     * 用户主动打断时调用，会清空消息队列
     * 打断后 result 返回时，handleQueueAfterResult 会检测到 'clear' 模式并清空队列
     *
     * 兜底机制：interrupt 请求返回后（无论成功/异常），如果 isGenerating 还是 true，立即清理
     */
    async function interrupt(): Promise<void> {
        if (!rsocketSession.value) {
            throw new Error('会话未连接')
        }

        // 设置打断模式为 clear（result 返回后会清空队列）
        messagesHandler.setInterruptMode('clear')

        try {
            await rsocketSession.value.interrupt()
            log.info(`[Tab ${tabId}] 中断请求已发送`)
        } catch (err) {
            log.error(`[Tab ${tabId}] 中断请求失败:`, err)
        } finally {
            // 兜底：如果 interrupt 返回后 isGenerating 还是 true，立即清理
            // （正常情况下应该由 result 消息触发清理，但后端异常时可能没有 result）
            if (messagesHandler.isGenerating.value) {
                log.warn(`[Tab ${tabId}] 中断返回后 isGenerating 仍为 true，手动清理`)
                messagesHandler.stopGenerating()
                messagesHandler.clearQueue()
            }
        }
    }

    /**
     * 将当前执行的任务切换到后台运行
     *
     * 这个功能允许用户继续其他操作，而当前任务在后台继续执行。
     * 仅在有活跃任务正在执行时有效。
     */
    async function runInBackground(): Promise<void> {
        if (!rsocketSession.value) {
            throw new Error('会话未连接')
        }

        if (!messagesHandler.isGenerating.value) {
            log.warn(`[Tab ${tabId}] 没有正在执行的任务，无法切换到后台`)
            return
        }

        try {
            await rsocketSession.value.runInBackground()
            log.info(`[Tab ${tabId}] 后台运行请求已发送`)
        } catch (err) {
            log.error(`[Tab ${tabId}] 后台运行请求失败:`, err)
            throw err
        }
    }

    /**
     * 将指定的 Bash 命令切换到后台运行
     *
     * 类似于官方 CLI 的 Ctrl+B 功能，但针对单个 Bash 命令。
     * 需要 CLI 应用 007-bash-background.js 补丁。
     *
     * @param taskId Bash 命令的 tool_use_id
     * @returns 后台运行结果
     */
    async function bashRunToBackground(taskId: string): Promise<BashRunToBackgroundResult> {
        if (!rsocketSession.value) {
            return { success: false, error: '会话未连接' }
        }

        try {
            const result = await rsocketSession.value.bashRunToBackground(taskId)
            log.info(`[Tab ${tabId}] Bash 后台运行结果: success=${result.success}, taskId=${result.taskId}`)
            return result
        } catch (err) {
            log.error(`[Tab ${tabId}] Bash 后台运行请求失败:`, err)
            return { success: false, error: String(err) }
        }
    }

    /**
     * 统一的后台运行方法
     *
     * 自动检测任务类型（Bash 或 Agent）并执行后台化。
     * 这是推荐的后台化方法，模拟 CLI 的 Ctrl+B 行为。
     *
     * @param taskId 可选的任务 ID：
     *   - 传入 taskId: 后台化指定任务（自动检测类型）
     *   - 不传 taskId: 后台化所有前台任务（Bash + Agent）
     * @returns 统一后台运行结果
     */
    async function runToBackground(taskId?: string): Promise<RunToBackgroundResult> {
        if (!rsocketSession.value) {
            return createRunToBackgroundError('会话未连接')
        }

        try {
            const result = await rsocketSession.value.runToBackground(taskId)
            if (taskId) {
                const typeInfo = result.isBash ? 'Bash' : 'Agent'
                log.info(`[Tab ${tabId}] ${typeInfo} 后台运行结果: success=${result.success}, taskId=${result.taskId}`)
            } else {
                log.info(`[Tab ${tabId}] 批量后台运行结果: success=${result.success}, bash=${result.bashCount}, agent=${result.agentCount}`)
            }
            return result
        } catch (err) {
            log.error(`[Tab ${tabId}] 后台运行请求失败:`, err)
            return createRunToBackgroundError(String(err))
        }
    }

    /**
     * 强制发送消息（打断当前生成并在打断完成后自动发送）
     *
     * 与普通 sendMessage 的区别：
     * - 如果正在生成：消息插队到队列最前面，发送打断请求，等待 result 返回后自动发送
     * - 如果没有生成：直接发送（与 sendMessage 相同）
     *
     * @param message - 消息内容
     * @param options - 发送选项
     */
    async function forceSendMessage(
        message: { contexts: any[]; contents: ContentBlock[]; ideContext?: ActiveFileInfo | null },
        options?: { isSlashCommand?: boolean }
    ): Promise<void> {
        // 如果是斜杠命令，清空 contexts 和 ideContext
        if (options?.isSlashCommand) {
            log.info(`[Tab ${tabId}] 检测到斜杠命令，忽略 contexts 和 ideContext`)
            message = {...message, contexts: [], ideContext: null}
        }

        // 如果正在生成，需要打断
        if (messagesHandler.isGenerating.value) {
            log.info(`[Tab ${tabId}] 强制发送：打断当前生成，消息插队`)

            // 1. 设置打断模式为 keep（保留队列并自动发送）
            messagesHandler.setInterruptMode('keep')

            // 2. 将消息插入队列最前面（不添加到 UI，等 result 返回后自动发送时再添加）
            messagesHandler.prependToQueue(message)

            // 3. 发送打断请求（result 返回后会自动发送队列中的第一条消息）
            if (rsocketSession.value) {
                await rsocketSession.value.interrupt()
            }
            return
        }

        // 没有生成中：直接添加到 UI 并发送
        log.info(`[Tab ${tabId}] 强制发送：直接处理消息`)
        const {userMessage, mergedContent} = messagesHandler.addMessageToUI(message)
        touch()

        // 发送消息到后端
        await sendMessageToBackend(userMessage, mergedContent, message)
    }

    /**
     * 编辑并重发消息（用于用户编辑历史消息后重新发送）
     *
     * 流程：
     * 1. 如果正在生成，打断当前生成
     * 2. 调用后端 truncateHistory API 截断 JSONL 历史文件
     * 3. 前端截断 displayItems 和 messages
     * 4. 断开当前连接
     * 5. 重连并恢复之前的会话 (resumeSessionId)
     * 6. 发送编辑后的消息
     *
     * @param uuid - 要截断的消息 UUID（该消息及其后的所有消息将被删除）
     * @param newMessage - 编辑后的新消息内容
     * @param projectPath - 项目路径（用于定位 JSONL 文件）
     */
    async function editAndResendMessage(
        uuid: string,
        newMessage: { contexts: any[]; contents: ContentBlock[] },
        projectPath: string
    ): Promise<void> {
        log.info(`[Tab ${tabId}] 🔄 编辑重发: uuid=${uuid}`)

        const currentSessionId = sessionId.value
        if (!currentSessionId) {
            throw new Error('会话未连接，无法编辑重发')
        }

        try {
            // 1. 如果正在生成，打断
            if (messagesHandler.isGenerating.value) {
                log.info(`[Tab ${tabId}] 正在生成中，先打断`)
                messagesHandler.setInterruptMode('clear')
                if (rsocketSession.value) {
                    await rsocketSession.value.interrupt()
                }
                messagesHandler.stopGenerating()
            }

            // 2. 调用后端 truncateHistory API（通过 aiAgentService，传入当前 session）
            log.info(`[Tab ${tabId}] 调用后端 truncateHistory API`)
            if (!rsocketSession.value) {
                throw new Error('会话未连接')
            }
            const truncateResult = await aiAgentService.truncateHistory(rsocketSession.value, {
                sessionId: currentSessionId,
                messageUuid: uuid,
                projectPath
            })

            if (!truncateResult.success) {
                throw new Error(truncateResult.error || '截断历史失败')
            }

            log.info(`[Tab ${tabId}] ✅ 后端历史截断成功: remainingLines=${truncateResult.remainingLines}`)

            // 3. 前端更新消息内容并截断其后的 displayItems 和 messages
            const truncatedData = messagesHandler.truncateMessages(uuid, {
                contexts: newMessage.contexts,
                contents: newMessage.contents
            })
            if (!truncatedData) {
                log.warn(`[Tab ${tabId}] 前端截断失败，但后端已截断，继续重发`)
            }

            // 4. 断开当前连接
            log.info(`[Tab ${tabId}] 断开当前连接`)
            await disconnect()

            // 5. 重连并恢复之前的会话
            log.info(`[Tab ${tabId}] 重连会话: resumeSessionId=${currentSessionId}`)
            await connect({
                ...initialConnectOptions.value,
                resumeSessionId: currentSessionId
            })

            // 6. 发送编辑后的消息（直接发送，不创建新的 UI 消息）
            log.info(`[Tab ${tabId}] 发送编辑后的消息`)
            if (truncatedData) {
                // 使用已更新的消息直接发送
                await sendMessageToBackend(truncatedData.userMessage, truncatedData.mergedContent, newMessage)
            } else {
                // 回退：如果前端截断失败，用常规方式发送（会创建新消息）
                await sendMessage(newMessage)
            }

            log.info(`[Tab ${tabId}] ✅ 编辑重发完成`)
        } catch (error) {
            log.error(`[Tab ${tabId}] ❌ 编辑重发失败:`, error)
            messagesHandler.addErrorMessage(`编辑重发失败: ${error instanceof Error ? error.message : String(error)}`)
            throw error
        }
    }

    // ========== 设置管理 ==========

    /**
     * 设置模型（需要重连才能生效）
     */
    async function setModel(model: string): Promise<void> {
        if (!rsocketSession.value) {
            modelId.value = model
            return
        }

        await rsocketSession.value.setModel(model)
        modelId.value = model
        log.info(`[Tab ${tabId}] 模型已设置: ${model}`)
    }

    /**
     * 设置权限模式
     */
    async function setPermissionModeValue(mode: RpcPermissionMode): Promise<void> {
        if (!rsocketSession.value) {
            permissionMode.value = mode
            return
        }

        await rsocketSession.value.setPermissionMode(mode)
        permissionMode.value = mode
        log.info(`[Tab ${tabId}] 权限模式已设置: ${mode}`)
    }

    /**
     * 设置思考级别
     */
    async function setThinkingLevelValue(level: ThinkingLevel): Promise<void> {
        if (!rsocketSession.value) {
            thinkingLevel.value = level
            return
        }

        // 调用 RSocket API 动态设置思考 token 上限
        // level: 0=Off, 2048=Think, 8096=Ultra
        const maxThinkingTokens = level === 0 ? 0 : level
        await rsocketSession.value.setMaxThinkingTokens(maxThinkingTokens)
        thinkingLevel.value = level
        log.info(`[Tab ${tabId}] 思考级别已设置: ${level}`)
    }

    /**
     * 仅更新本地权限模式状态，不调用后端 RPC
     * 用于 SDK 会自行处理模式切换的场景（如权限建议中的 setMode）
     */
    function setLocalPermissionMode(mode: RpcPermissionMode): void {
        permissionMode.value = mode
        log.info(`[Tab ${tabId}] 本地权限模式已更新: ${mode}`)
    }

    /**
     * 设置更新选项
     */
    interface SettingsUpdate {
        model?: string
        permissionMode?: RpcPermissionMode
        thinkingLevel?: ThinkingLevel
        skipPermissions?: boolean
    }

    /**
     * 智能更新设置
     *
     * 策略：
     * - 所有设置（model, permissionMode, thinkingLevel）：都可以通过 RPC 动态设置
     * - skipPermissions：纯前端行为，只更新本地状态
     */
    async function updateSettings(settings: SettingsUpdate): Promise<void> {
        // 如果未连接，只更新本地状态
        if (!sessionId.value || connectionState.status !== ConnectionStatus.CONNECTED) {
            if (settings.model !== undefined) modelId.value = settings.model
            if (settings.permissionMode !== undefined) permissionMode.value = settings.permissionMode
            if (settings.thinkingLevel !== undefined) thinkingLevel.value = settings.thinkingLevel
            if (settings.skipPermissions !== undefined) skipPermissions.value = settings.skipPermissions
            log.info(`[Tab ${tabId}] 未连接，仅更新本地设置`)
            return
        }

        // 通过 RPC 动态更新设置
        log.info(`[Tab ${tabId}] 通过 RPC 更新设置: `, settings)

        if (settings.model !== undefined) {
            await setModel(settings.model)
        }
        if (settings.permissionMode !== undefined) {
            await setPermissionModeValue(settings.permissionMode)
        }
        if (settings.thinkingLevel !== undefined) {
            await setThinkingLevelValue(settings.thinkingLevel)
        }
        if (settings.skipPermissions !== undefined) {
            skipPermissions.value = settings.skipPermissions
        }
    }

    // ========== 多后端方法 ==========

    /**
     * 设置后端类型 (provider)
     *
     * 统一使用 RSocket 协议，切换后端只需要：
     * 1. 断开现有 RSocket 连接
     * 2. 更新 backendType
     * 3. 重置 modelId 为新后端的默认模型
     * 4. 下次 connect 时会自动传递新的 provider 参数
     */
    async function setBackendType(type: BackendType): Promise<void> {
        if (backendType.value === type) {
            log.debug(`[Tab ${tabId}] 后端类型未变化: ${type}`)
            return
        }

        log.info(`[Tab ${tabId}] 切换后端类型 (provider): ${backendType.value} -> ${type}`)
        invalidateConnectAttempt(`switch-backend:${backendType.value}->${type}`)

        // 断开现有 RSocket 连接（统一协议，只需管理一个连接）
        if (rsocketSession.value) {
            log.info(`[Tab ${tabId}] 断开 RSocket 连接`)
            rsocketSession.value.disconnect()
            rsocketSession.value = null
            sessionId.value = null
        }

        if (backendSession.value) {
            log.info(`[Tab ${tabId}] 断开 BackendSession 连接`)
            backendSession.value.disconnect()
            backendSession.value = null
            backendConfig.value = null
            sessionId.value = null
        }

        // 重置连接状态
        connectionState.status = ConnectionStatus.DISCONNECTED
        connectionState.lastError = null

        // 设置新的后端类型（下次 connect 时会自动使用新的 provider）
        backendType.value = type

        // 重置 modelId 为新后端的默认模型，避免 Claude 模型出现在 Codex 列表中
        const settingsStore = useSettingsStore()
        const newDefaultModel = type === 'claude'
            ? settingsStore.settings.claudeModel
            : settingsStore.settings.codexModel
        log.info(`[Tab ${tabId}] 重置模型为新后端默认: ${modelId.value} -> ${newDefaultModel}`)
        modelId.value = newDefaultModel

        log.info(`[Tab ${tabId}] 后端类型已切换: ${type}`)
    }

    /**
     * 使用 BackendSession 连接（新架构）
     *
     * 根据 backendType 创建对应的会话实例
     */
    async function connectWithBackend(options: TabConnectOptions = {}): Promise<void> {
        const resolvedOptions: TabConnectOptions = {...(initialConnectOptions.value || {}), ...options}

        if (connectionState.status === ConnectionStatus.CONNECTING) {
            log.warn(`[Tab ${tabId}] 正在连接中，将强制中断并重新连接`)
        }

        invalidateConnectAttempt('connect-with-backend')

        if (rsocketSession.value) {
            log.info(`[Tab ${tabId}] 断开 RSocket 连接`)
            rsocketSession.value.disconnect()
            rsocketSession.value = null
            sessionId.value = null
        }

        // 如果已有后端会话，先断开
        if (backendSession.value) {
            log.info(`[Tab ${tabId}] 断开旧的后端会话`)
            backendSession.value.disconnect()
            backendSession.value = null
        }

        connectionState.status = ConnectionStatus.CONNECTING
        connectionState.lastError = null

        try {
            const settingsStore = useSettingsStore()

            // 根据后端类型获取配置
            const config = settingsStore.getBackendConfig(backendType.value)
            if (!config) {
                throw new Error(`无法获取 ${backendType.value} 后端配置`)
            }

            // 使用工厂创建会话
            const session = BackendSessionFactory.createSession(backendType.value, config)

            // 订阅 BackendSession 事件
            session.onEvent(handleBackendEvent)
            session.onConnectionStatusChange(handleBackendConnectionStatus)

            // 连接选项（connectId 由后端分配，不再前端传递）
            const connectOptions = {
                config,
                continueConversation: resolvedOptions.continueConversation,
                resumeSessionId: resolvedOptions.resumeSessionId,
                projectPath: projectPath.value || undefined
            }

            // 连接
            await session.connect(connectOptions)

            // 保存会话实例
            backendSession.value = session
            backendConfig.value = config
            sessionId.value = session.getSessionId()
            connectionState.status = ConnectionStatus.CONNECTED
            connectionState.lastError = null

            log.info(`[Tab ${tabId}] BackendSession 连接成功: ${backendType.value}, sessionId=${sessionId.value}`)

            // 连接成功，重置重连计数
            reconnectAttempts = 0

            // 更新设置
            updateLastAppliedSettings()
            pendingSettings.value = {}

        } catch (error) {
            connectionState.status = ConnectionStatus.ERROR
            connectionState.lastError = error instanceof Error ? error.message : String(error)
            log.error(`[Tab ${tabId}] BackendSession 连接失败:`, error)
            throw error
        }
    }

    /**
     * 处理 BackendSession 事件（将 Codex BackendEvent 转换为 Claude RPC 流式事件格式）
     * 使用 CodexRpcAdapter 进行事件转换
     */
    function handleBackendEvent(event: import('@/types/backend').BackendEvent): void {
        const adapter = getCodexRpcAdapter()
        
        switch (event.type) {
            case 'text_delta':
                adapter.handleTextDelta(event.text, 'text')
                break

            case 'thinking_delta':
                adapter.handleTextDelta(event.text, 'thinking')
                break

            case 'tool_started':
                adapter.handleToolStarted({
                    itemId: event.itemId,
                    toolName: event.toolName,
                    toolType: event.toolType,
                    parameters: event.parameters
                })
                break

            case 'tool_completed':
                adapter.handleToolCompleted({
                    itemId: event.itemId,
                    success: event.success,
                    result: event.result
                })
                break

            case 'turn_completed':
                adapter.handleTurnCompleted(
                    { turnId: event.turnId, status: event.status },
                    processNextQueuedMessage
                )
                break

            case 'approval_request':
                log.info(`[Tab ${tabId}] 收到审批请求: ${event.approvalType}`)
                if (backendType.value === 'codex') {
                    handleCodexApprovalRequest(event)
                }
                break

            case 'error':
                log.error(`[Tab ${tabId}] 后端错误: ${event.code} - ${event.message}`)
                messagesHandler.addErrorMessage(`后端错误: ${event.message}`)
                break
        }

        touch()
    }

    /**
     * 处理 Codex 审批请求
     */
    function handleCodexApprovalRequest(event: import('@/types/backend').ApprovalRequestEvent): void {
        if (skipPermissions.value) {
            log.info(`[Tab ${tabId}] skipPermissions 已启用，自动放行 Codex 审批请求: ${event.approvalType}`)
            if (backendSession.value) {
                backendSession.value.respondToApproval({
                    requestId: event.requestId,
                    approved: true
                })
            }
            return
        }

        const details = event.details as Record<string, unknown>
        const matchedToolCallId = (details as any).itemId || (details as any).item_id
        const request: Omit<PendingPermissionRequest, 'createdAt'> = {
            id: event.requestId,
            sessionId: sessionId.value!,
            toolName: event.approvalType === 'command' ? 'CommandExecution' : 'FileChange',
            input: details,
            matchedToolCallId: matchedToolCallId ? String(matchedToolCallId) : undefined,
            resolve: (response) => {
                // 发送审批响应到后端
                if (backendSession.value) {
                    backendSession.value.respondToApproval({
                        requestId: event.requestId,
                        approved: response.approved,
                        reason: response.denyReason
                    })
                }
            },
            reject: (error) => {
                log.error(`[Tab ${tabId}] 审批请求被拒绝:`, error)
            }
        }
        permissions.addPermissionRequest(request)
    }

    /**
     * 处理 BackendSession 连接状态变化
     */
    function handleBackendConnectionStatus(status: import('@/types/backend').BackendConnectionStatus): void {
        switch (status) {
            case 'connected':
                connectionState.status = ConnectionStatus.CONNECTED
                break
            case 'connecting':
                connectionState.status = ConnectionStatus.CONNECTING
                break
            case 'disconnected':
                connectionState.status = ConnectionStatus.DISCONNECTED
                break
            case 'error':
                connectionState.status = ConnectionStatus.ERROR
                break
        }
        log.info(`[Tab ${tabId}] 后端连接状态: ${status}`)
    }

    /**
     * 使用 BackendSession 发送消息
     */
    async function sendMessageWithBackend(
        message: { contexts: any[]; contents: ContentBlock[]; ideContext?: ActiveFileInfo | null }
    ): Promise<void> {
        if (!backendSession.value) {
            throw new Error('后端会话未连接')
        }

        // 转换消息格式
        const userMessage: import('@/services/backend').UserMessage = {
            contents: message.contents.map(c => {
                if (c.type === 'text') {
                    return { type: 'text' as const, text: (c as any).text }
                } else if (c.type === 'image') {
                    return { type: 'image' as const, data: (c as any).source?.data || '', mimeType: (c as any).source?.media_type }
                }
                return { type: 'text' as const, text: JSON.stringify(c) }
            }),
            contexts: message.contexts?.map(ctx => ({
                type: 'file' as const,
                path: ctx.path,
                content: ctx.content
            }))
        }

        messagesHandler.startGenerating(`msg-${Date.now()}`)
        backendSession.value.sendMessage(userMessage)
    }

    /**
     * 获取当前后端的能力信息
     */
    function getBackendCapabilities() {
        return getCapabilities(backendType.value)
    }

    /**
     * 检查后端是否支持特定功能
     */
    function isFeatureSupported(feature: BackendFeature): boolean {
        return supportsFeature(backendType.value, feature)
    }

    /**
     * 获取思考配置
     */
    function getThinkingConfig(): ThinkingConfig | null {
        if (!backendConfig.value) {
            return null
        }

        if (backendConfig.value.type === 'claude') {
            return {
                type: 'claude',
                enabled: backendConfig.value.thinkingEnabled ?? true,
                tokenBudget: backendConfig.value.thinkingTokenBudget ?? 8096
            }
        } else {
            return {
                type: 'codex',
                effort: backendConfig.value.reasoningEffort,
                summary: backendConfig.value.reasoningSummary
            }
        }
    }

    /**
     * 设置思考配置
     */
    async function setThinkingConfig(config: ThinkingConfig): Promise<void> {
        if (!backendSession.value) {
            log.warn(`[Tab ${tabId}] 后端会话未连接，无法设置思考配置`)
            return
        }

        try {
            await backendSession.value.updateThinkingConfig(config)
            log.info(`[Tab ${tabId}] 思考配置已更新:`, config)
        } catch (error) {
            log.error(`[Tab ${tabId}] 设置思考配置失败:`, error)
            throw error
        }
    }

    // ========== 辅助方法 ==========

    /**
     * 更新活跃时间
     */
    function touch(): void {
        const now = Date.now()
        updatedAt.value = now
        lastActiveAt.value = now
    }

    /**
     * 重命名
     */
    function rename(newName: string): void {
        name.value = newName
        touch()
    }

    /**
     * 设置排序
     */
    function setOrder(newOrder: number): void {
        order.value = newOrder
    }

    /**
     * 保存 UI 状态
     */
    function saveUiState(state: Partial<UIState>): void {
        if (state.inputText !== undefined) uiState.inputText = state.inputText
        if (state.contexts !== undefined) uiState.contexts = state.contexts
        if (state.autoCleanupContexts !== undefined) uiState.autoCleanupContexts = state.autoCleanupContexts
        if (state.scrollState !== undefined) {
            // 深度合并 scrollState
            Object.assign(uiState.scrollState, state.scrollState)
        }
    }

    /**
     * 重置 Tab
     */
    function reset(): void {
        // 重置所有子 composables
        tools.reset()
        stats.reset()
        permissions.reset()
        messagesHandler.reset()
        resetHistoryState()

        // 重置 UI 状态（保留输入框内容和附件）
        // uiState.inputText = ''   // 保留输入框内容
        // uiState.contexts = []    // 保留附件池
        Object.assign(uiState.scrollState, DEFAULT_SCROLL_STATE)

        // 重置错误状态
        connectionState.lastError = null

        log.debug(`[Tab ${tabId}] 已重置`)
    }

    // ========== 导出 ==========

    return {
        // Tab 标识
        tabId,
        /** 后端分配的连接标识，用于 MCP 路由（与 RSocket 连接生命周期绑定） */
        connectId,

        // 基础信息（响应式）
        sessionId,
        projectPath,
        name,
        order,
        updatedAt,
        lastActiveAt,

        // 连接状态（reactive 对象，支持响应式追踪）
        connectionState,
        // 为了向后兼容，提供直接访问的 getter
        get connectionStatus() {
            return connectionState.status
        },
        get capabilities() {
            return connectionState.capabilities
        },
        get lastError() {
            return connectionState.lastError
        },

        // 连接设置
        modelId,
        thinkingLevel,
        permissionMode,
        skipPermissions,
        resumeFromSessionId,
        initialConnectOptions,

        // MCP 服务器状态
        mcpServers,

        // 多后端支持
        backendType,
        backendConfig,
        backendSession,

        // UI 状态
        uiState,

        // 压缩状态
        isCompacting,
        compactMetadata,

        // 计算属性
        isConnected,
        isConnecting,
        hasError,
        isGenerating,
        tabInfo,

        // 子 composables（暴露以便直接访问）
        tools,
        stats,
        permissions,
        fileChanges,

        // 消息相关（直接暴露 messagesHandler 的状态）
        messages: messagesHandler.messages,
        displayItems: messagesHandler.displayItems,
        messageQueue: messagesHandler.messageQueue,

        // 连接管理
        setInitialConnectOptions,
        connect,
        disconnect,
        disposeSession,
        reconnect,
        reloadSession,

        // 消息发送
        sendMessage,
        sendTextMessage,
        sendTextMessageDirect,
        forceSendMessage,
        interrupt,
        runInBackground,
        bashRunToBackground,
        runToBackground,
        editAndResendMessage,

        // 队列管理
        editQueueMessage: messagesHandler.editQueueMessage,
        removeFromQueue: messagesHandler.removeFromQueue,
        clearQueue: messagesHandler.clearQueue,

        // 设置管理
        setModel,
        setPermissionMode: setPermissionModeValue,
        setThinkingLevel: setThinkingLevelValue,
        setLocalPermissionMode,
        updateSettings,
        setPendingSetting,
        pendingSettings,
        lastAppliedSettings,

        // 历史状态
        historyState,

        // 辅助方法
        touch,
        rename,
        setOrder,
        saveUiState,
        reset,

        // 历史回放
        loadHistory,
        loadMoreHistory,

        // 多后端方法
        setBackendType,
        connectWithBackend,
        sendMessageWithBackend,
        getBackendCapabilities,
        isFeatureSupported,
        getThinkingConfig,
        setThinkingConfig,

        // 会话实例访问（用于外部组件检查连接状态）
        get session() {
            return rsocketSession.value
        },
        // 后端会话访问（新架构）
        get backendSessionInstance() {
            return backendSession.value
        }
    }
}

/**
 * useSessionTab 返回类型
 */
export type SessionTabInstance = ReturnType<typeof useSessionTab>
