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

import {ref, reactive, computed} from 'vue'
import {aiAgentService} from '@/services/aiAgentService'
import type {ConnectOptions} from '@/services/aiAgentService'
import type {ContentBlock, Message} from '@/types/message'
import {ConnectionStatus} from '@/types/display'
import type {RpcCapabilities, RpcPermissionMode, RpcMessage, RpcStreamEvent, RpcResultMessage} from '@/types/rpc'
import {
    isStreamEvent as isRpcStreamEvent,
    isResultMessage as isRpcResultMessage,
    isAssistantMessage as isRpcAssistantMessage,
    isUserMessage as isRpcUserMessage,
    type RpcStatusSystemMessage,
    type RpcCompactBoundaryMessage,
    type RpcCompactMetadata
} from '@/types/rpc'
import {mapRpcMessageToMessage} from '@/utils/rpcMappers'
import {useSessionTools, type SessionToolsInstance} from './useSessionTools'
import {useSessionStats, type SessionStatsInstance} from './useSessionStats'
import {useSessionPermissions, type SessionPermissionsInstance} from './useSessionPermissions'
import {useSessionMessages, type SessionMessagesInstance} from './useSessionMessages'
import {loggers} from '@/utils/logger'
import type {PendingPermissionRequest, PendingUserQuestion, PermissionResponse} from '@/types/permission'
import {HISTORY_LAZY_LOAD_SIZE, HISTORY_PAGE_SIZE} from '@/constants/messageWindow'

const log = loggers.session

/**
 * UI 状态（用于切换会话时保存/恢复）
 */
export interface UIState {
    inputText: string
    contexts: any[]
    scrollPosition: number
    newMessageCount: number  // 滚动按钮上的新消息计数
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
 * 连接配置
 */
export interface TabConnectOptions {
    model?: string
    thinkingEnabled?: boolean
    permissionMode?: RpcPermissionMode
    skipPermissions?: boolean
    continueConversation?: boolean
    resume?: string
}

/**
 * 设置 key 常量（避免字符串字面量）
 */
export const SETTING_KEYS = {
    MODEL: 'model',
    PERMISSION_MODE: 'permissionMode',
    THINKING_ENABLED: 'thinkingEnabled',
    SKIP_PERMISSIONS: 'skipPermissions',
} as const

export type SettingKey = typeof SETTING_KEYS[keyof typeof SETTING_KEYS]

/**
 * RPC 消息规范化结果类型
 */
export type NormalizedRpcMessage =
    | { kind: 'message'; data: any }
    | { kind: 'stream_event'; data: RpcStreamEvent }
    | { kind: 'result'; data: RpcResultMessage }
    | { kind: 'status_system'; data: RpcStatusSystemMessage }
    | { kind: 'compact_boundary'; data: RpcCompactBoundaryMessage }

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

    // ========== Tab 基础信息 ==========
    const tabId = `tab-${Date.now()}-${Math.random().toString(36).substring(2, 8)}`
    const sessionId = ref<string | null>(null)
    const name = ref('新会话')
    const createdAt = Date.now()
    const updatedAt = ref(createdAt)
    const lastActiveAt = ref(createdAt)
    const order = ref(initialOrder)

    // ========== 连接状态 ==========
    // 使用 reactive 对象而不是 ref，以便在 shallowRef 容器中也能被追踪
    const connectionState = reactive({
        status: ConnectionStatus.DISCONNECTED as ConnectionStatus,
        capabilities: null as RpcCapabilities | null,
        lastError: null as string | null
    })

    // ========== 连接设置（连接时确定，切换需要重连）==========
    const modelId = ref<string | null>(null)
    const thinkingEnabled = ref(true)
    const permissionMode = ref<RpcPermissionMode>('default')
    const skipPermissions = ref(false)
    const initialConnectOptions = ref<TabConnectOptions | null>(null)

    // ========== 设置延迟应用机制 ==========
    // 上次 query 时实际应用的设置
    const lastAppliedSettings = ref<{
        model: string | null
        permissionMode: RpcPermissionMode
        thinkingEnabled: boolean
        skipPermissions: boolean
    }>({
        model: null,
        permissionMode: 'default',
        thinkingEnabled: true,
        skipPermissions: false
    })

    // 待应用的设置（UI 修改后保存到这里，下次 query 时应用）
    const pendingSettings = ref<{
        model?: string
        permissionMode?: RpcPermissionMode
        thinkingEnabled?: boolean
        skipPermissions?: boolean
    }>({})

    function setInitialConnectOptions(options: TabConnectOptions) {
        initialConnectOptions.value = {...options}
        if (options.model) modelId.value = options.model
        if (options.thinkingEnabled !== undefined) thinkingEnabled.value = options.thinkingEnabled
        if (options.permissionMode) permissionMode.value = options.permissionMode
        if (options.skipPermissions !== undefined) skipPermissions.value = options.skipPermissions
    }

    // ========== UI 状态 ==========
    const uiState = reactive<UIState>({
        inputText: '',
        contexts: [],
        scrollPosition: 0,
        newMessageCount: 0
    })

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
    const resumeFromSessionId = computed(() => initialConnectOptions.value?.resume ?? null)

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

        // 5. 尝试识别 assistant / user 消息
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

    // ========== 连接管理 ==========

    // 重连配置
    const MAX_RECONNECT_ATTEMPTS = 3
    const RECONNECT_DELAY = 2000 // 2秒
    let reconnectAttempts = 0
    let reconnectTimer: ReturnType<typeof setTimeout> | null = null

    // 断开事件订阅取消函数
    let unsubscribeDisconnect: (() => void) | null = null

    /**
     * 处理会话被动断开
     */
    function handleSessionDisconnected(error?: Error): void {
        log.warn(`[Tab ${tabId}] 会话被动断开`, error?.message)

        // 更新连接状态
        connectionState.status = ConnectionStatus.DISCONNECTED
        connectionState.lastError = error?.message || '连接已断开'
        sessionId.value = null

        // 停止生成状态
        if (messagesHandler.isGenerating.value) {
            messagesHandler.stopGenerating()
        }

        // 取消所有待处理的权限和问题
        permissions.cancelAllPermissions('连接断开')
        permissions.cancelAllQuestions('连接断开')

        // 清理订阅
        if (unsubscribeDisconnect) {
            unsubscribeDisconnect()
            unsubscribeDisconnect = null
        }

        // 显示错误提示消息
        messagesHandler.addErrorMessage('连接已断开，请点击重新连接')
    }

    /**
     * 连接到后端
     */
    async function connect(options: TabConnectOptions = {}): Promise<void> {
        const resolvedOptions: TabConnectOptions = {...(initialConnectOptions.value || {}), ...options}
        if (connectionState.status === ConnectionStatus.CONNECTING) {
            log.warn(`[Tab ${tabId}] 正在连接中，请勿重复连接`)
            return
        }

        // 如果已有连接，先断开旧连接
        if (sessionId.value) {
            log.info(`[Tab ${tabId}] 断开旧连接: ${sessionId.value}`)
            try {
                await aiAgentService.disconnect(sessionId.value)
            } catch (e) {
                log.warn(`[Tab ${tabId}] 断开旧连接失败:`, e)
            }
            sessionId.value = null
        }

        connectionState.status = ConnectionStatus.CONNECTING
        connectionState.lastError = null

        // 不覆盖 ref 值！
        // 初始值已在 setInitialConnectOptions 中设置
        // 用户修改通过 setPendingSetting 直接更新 ref
        // connect 直接使用当前 ref 值构建请求

        try {
            const connectOptions: ConnectOptions = {
                includePartialMessages: true,
                allowDangerouslySkipPermissions: true,
                model: modelId.value || undefined,
                thinkingEnabled: thinkingEnabled.value,
                permissionMode: permissionMode.value,
                dangerouslySkipPermissions: skipPermissions.value,
                continueConversation: resolvedOptions.continueConversation,
                resume: resolvedOptions.resume
            }

            const result = await aiAgentService.connect(connectOptions, handleMessage)

            sessionId.value = result.sessionId
            connectionState.capabilities = result.capabilities
            connectionState.status = ConnectionStatus.CONNECTED
            connectionState.lastError = null

            // 连接成功，重置重连计数
            reconnectAttempts = 0

            // 订阅会话断开事件
            unsubscribeDisconnect = aiAgentService.onSessionDisconnect((sid, error) => {
                if (sid === sessionId.value) {
                    handleSessionDisconnected(error)
                }
            })

            // 设置处理队列前的回调（用于应用 pending settings）
            messagesHandler.setBeforeProcessQueueFn(async () => {
                log.debug(`[Tab ${tabId}] 处理队列前，应用 pending settings`)
                await applyPendingSettingsIfNeeded()
            })

            // 注册双向 RPC 处理器
            registerRpcHandlers()

            // 连接成功后，更新 lastAppliedSettings 并清空 pendingSettings
            updateLastAppliedSettings()
            pendingSettings.value = {}

            log.info(`[Tab ${tabId}] 连接成功: sessionId=${result.sessionId}`)

            // 连接成功后，处理队列中的消息
            processNextQueuedMessage()
        } catch (error) {
            connectionState.status = ConnectionStatus.ERROR
            connectionState.lastError = error instanceof Error ? error.message : String(error)
            log.error(`[Tab ${tabId}] 连接失败:`, error)

            // 自动重连
            scheduleReconnect(options)
        }
    }

    /**
     * 安排自动重连
     */
    function scheduleReconnect(options: TabConnectOptions): void {
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

        reconnectTimer = setTimeout(async () => {
            reconnectTimer = null
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
        // 取消自动重连
        cancelReconnect()

        // 先取消订阅，避免主动断开触发 handleSessionDisconnected
        if (unsubscribeDisconnect) {
            unsubscribeDisconnect()
            unsubscribeDisconnect = null
        }

        if (sessionId.value) {
            try {
                await aiAgentService.disconnect(sessionId.value)
            } catch (error) {
                log.warn(`[Tab ${tabId}] 断开连接失败:`, error)
            }
        }

        sessionId.value = null
        connectionState.status = ConnectionStatus.DISCONNECTED

        // 取消所有待处理的权限和问题
        permissions.cancelAllPermissions('Tab disconnected')
        permissions.cancelAllQuestions('Tab disconnected')

        log.info(`[Tab ${tabId}] 已断开连接`)
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
                {...params, offset, limit}
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
            const metadata = await aiAgentService.getHistoryMetadata(params)
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
        if (!sessionId.value) {
            // 如果没有会话，走完整的 connect 流程
            await connect(options || {
                model: modelId.value || undefined,
                thinkingEnabled: thinkingEnabled.value,
                permissionMode: permissionMode.value,
                skipPermissions: skipPermissions.value
            })
            return
        }

        connectionState.status = ConnectionStatus.CONNECTING
        connectionState.lastError = null

        // 更新本地设置
        if (options?.model) modelId.value = options.model
        if (options?.thinkingEnabled !== undefined) thinkingEnabled.value = options.thinkingEnabled
        if (options?.permissionMode) permissionMode.value = options.permissionMode
        if (options?.skipPermissions !== undefined) skipPermissions.value = options.skipPermissions

        try {
            const connectOptions: ConnectOptions = {
                includePartialMessages: true,
                allowDangerouslySkipPermissions: true,
                model: modelId.value || undefined,
                thinkingEnabled: thinkingEnabled.value,
                permissionMode: permissionMode.value,
                dangerouslySkipPermissions: skipPermissions.value,
                continueConversation: options?.continueConversation,
                resume: options?.resume
            }

            // 使用 reconnectSession 复用 WebSocket
            const result = await aiAgentService.reconnectSession(sessionId.value, connectOptions)

            sessionId.value = result.sessionId
            connectionState.capabilities = result.capabilities
            connectionState.status = ConnectionStatus.CONNECTED
            connectionState.lastError = null

            // 连接成功后，更新 lastAppliedSettings 并清空 pendingSettings
            updateLastAppliedSettings()
            pendingSettings.value = {}

            log.info(`[Tab ${tabId}] 重连成功: sessionId=${result.sessionId}`)

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

    // ========== RPC 处理器注册 ==========

    /**
     * 注册双向 RPC 处理器
     */
    function registerRpcHandlers(): void {
        if (!sessionId.value) return

        // 注册 AskUserQuestion 处理器
        // Protobuf 模式：参数已经是解码后的 AskUserQuestionParams 对象
        aiAgentService.register(sessionId.value, 'AskUserQuestion', async (params: Record<string, any>) => {
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
        aiAgentService.register(sessionId.value, 'RequestPermission', async (params: Record<string, any>) => {
            const toolName = params.toolName || 'Unknown'
            const toolUseId = params.toolUseId
            const input = params.input || {}
            const permissionSuggestions = params.permissionSuggestions

            log.info(`[Tab ${tabId}] 收到权限请求: ${toolName}`)

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
     */
    async function ensureConnected(): Promise<void> {
        if (connectionState.status === ConnectionStatus.CONNECTED) {
            return
        }

        if (connectionState.status === ConnectionStatus.CONNECTING) {
            log.info(`[Tab ${tabId}] 正在连接中，等待连接完成...`)
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

        log.info(`[Tab ${tabId}] 连接未建立，开始连接...`)
        await connect(initialConnectOptions.value || {})
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
        message: { contexts: any[]; contents: ContentBlock[] },
        options?: { isSlashCommand?: boolean }
    ): Promise<void> {
        // 如果是斜杠命令，清空 contexts
        if (options?.isSlashCommand) {
            log.info(`[Tab ${tabId}] 检测到斜杠命令，忽略 contexts`)
            message = {...message, contexts: []}
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
     */
    async function sendMessageToBackend(
        userMessage: Message,
        mergedContent: ContentBlock[],
        originalMessage: { contexts: any[]; contents: ContentBlock[] }
    ): Promise<void> {
        try {
            // 应用待定设置（可能触发重连）
            log.info(`[Tab ${tabId}] 应用待定设置...`)
            await applyPendingSettingsIfNeeded()

            // 确保连接就绪
            log.info(`[Tab ${tabId}] 确保连接就绪...`)
            await ensureConnected()

            if (!sessionId.value) {
                throw new Error('会话未连接')
            }

            // 设置生成状态
            messagesHandler.startGenerating(userMessage.id)
            log.info(`[Tab ${tabId}] 开始发送消息到后端...`)

            // 直接调用 aiAgentService 发送
            await aiAgentService.sendMessageWithContent(sessionId.value, mergedContent as any)
            log.info(`[Tab ${tabId}] 消息发送完成`)
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
     * 中断当前操作
     *
     * 注意：此方法只发送中断请求，不会清空消息队列
     * 如需清空队列，请在调用前显式调用 clearQueue()
     */
    async function interrupt(): Promise<void> {
        if (!sessionId.value) {
            throw new Error('会话未连接')
        }

        await aiAgentService.interrupt(sessionId.value)
        log.info(`[Tab ${tabId}] 中断请求已发送`)
    }

    /**
     * 强制发送消息（打断当前生成并立即发送）
     *
     * 与普通 sendMessage 的区别：
     * - 先发送打断请求
     * - 立即停止本地生成状态（不等待后端响应）
     * - 跳过队列检查，直接发送消息
     *
     * @param message - 消息内容
     * @param options - 发送选项
     */
    async function forceSendMessage(
        message: { contexts: any[]; contents: ContentBlock[] },
        options?: { isSlashCommand?: boolean }
    ): Promise<void> {
        // 如果正在生成，先打断
        if (messagesHandler.isGenerating.value) {
            log.info(`[Tab ${tabId}] 强制发送：先打断当前生成`)
            // 发送打断请求（不等待后端响应）
            if (sessionId.value) {
                aiAgentService.interrupt(sessionId.value).catch(err => {
                    log.warn(`[Tab ${tabId}] 打断请求失败:`, err)
                })
            }
            // 立即停止本地生成状态
            messagesHandler.stopGenerating()
        }

        // 如果是斜杠命令，清空 contexts
        if (options?.isSlashCommand) {
            log.info(`[Tab ${tabId}] 检测到斜杠命令，忽略 contexts`)
            message = {...message, contexts: []}
        }

        // 直接添加到 UI 并发送（跳过队列检查）
        log.info(`[Tab ${tabId}] 强制发送：直接处理消息`)
        const {userMessage, mergedContent} = messagesHandler.addMessageToUI(message)
        touch()

        // 发送消息到后端
        await sendMessageToBackend(userMessage, mergedContent, message)
    }

    // ========== 设置管理 ==========

    /**
     * 设置模型（需要重连才能生效）
     */
    async function setModel(model: string): Promise<void> {
        if (!sessionId.value) {
            modelId.value = model
            return
        }

        await aiAgentService.setModel(sessionId.value, model)
        modelId.value = model
        log.info(`[Tab ${tabId}] 模型已设置: ${model}`)
    }

    /**
     * 设置权限模式
     */
    async function setPermissionModeValue(mode: RpcPermissionMode): Promise<void> {
        if (!sessionId.value) {
            permissionMode.value = mode
            return
        }

        await aiAgentService.setPermissionMode(sessionId.value, mode)
        permissionMode.value = mode
        log.info(`[Tab ${tabId}] 权限模式已设置: ${mode}`)
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
        thinkingEnabled?: boolean
        skipPermissions?: boolean
    }

    /**
     * 智能更新设置
     *
     * 策略：
     * - 有 RPC API 的设置（model, permissionMode）：直接调用 RPC
     * - 无 RPC API 的设置（thinkingEnabled, skipPermissions）：需要重连
     * - 混合修改且包含需要重连的：统一重连，所有参数通过 connect 传递
     */
    async function updateSettings(settings: SettingsUpdate): Promise<void> {
        const hasRpcSettings = settings.model !== undefined || settings.permissionMode !== undefined
        const hasReconnectSettings = settings.thinkingEnabled !== undefined || settings.skipPermissions !== undefined

        // 如果未连接，只更新本地状态
        if (!sessionId.value || connectionState.status !== ConnectionStatus.CONNECTED) {
            if (settings.model !== undefined) modelId.value = settings.model
            if (settings.permissionMode !== undefined) permissionMode.value = settings.permissionMode
            if (settings.thinkingEnabled !== undefined) thinkingEnabled.value = settings.thinkingEnabled
            if (settings.skipPermissions !== undefined) skipPermissions.value = settings.skipPermissions
            log.info(`[Tab ${tabId}] 未连接，仅更新本地设置`)
            return
        }

        // 需要重连的情况：包含 thinkingEnabled 或 skipPermissions
        if (hasReconnectSettings) {
            log.info(`[Tab ${tabId}] 设置需要重连: `, settings)

            // 先更新本地状态
            if (settings.model !== undefined) modelId.value = settings.model
            if (settings.permissionMode !== undefined) permissionMode.value = settings.permissionMode
            if (settings.thinkingEnabled !== undefined) thinkingEnabled.value = settings.thinkingEnabled
            if (settings.skipPermissions !== undefined) skipPermissions.value = settings.skipPermissions

            // 重连，所有设置通过 connect 参数传递
            await reconnect({
                model: modelId.value || undefined,
                thinkingEnabled: thinkingEnabled.value,
                permissionMode: permissionMode.value,
                skipPermissions: skipPermissions.value
            })
            return
        }

        // 只有 RPC 设置，直接调用 RPC
        if (hasRpcSettings) {
            log.info(`[Tab ${tabId}] 通过 RPC 更新设置: `, settings)

            if (settings.model !== undefined) {
                await setModel(settings.model)
            }
            if (settings.permissionMode !== undefined) {
                await setPermissionModeValue(settings.permissionMode)
            }
        }
    }

    // ========== 设置延迟应用 ==========

    /**
     * 保存设置到 pending（不立即应用）
     * UI 会立即反映新值，但实际设置在下次 query 时才生效
     */
    function setPendingSetting<K extends keyof typeof pendingSettings.value>(
        key: K,
        value: NonNullable<typeof pendingSettings.value[K]>
    ): void {
        log.info(`[Tab ${tabId}] 保存 pending 设置: ${key} = ${value}`)

        // 更新 pending
        pendingSettings.value = {...pendingSettings.value, [key]: value}

        // 同时更新本地 ref（UI 立即反映）
        if (key === 'model') modelId.value = value as string
        if (key === 'permissionMode') permissionMode.value = value as RpcPermissionMode
        if (key === 'thinkingEnabled') thinkingEnabled.value = value as boolean
        if (key === 'skipPermissions') skipPermissions.value = value as boolean
    }

    /**
     * 检查是否有待应用的设置
     */
    function hasPendingSettings(): boolean {
        return Object.keys(pendingSettings.value).length > 0
    }

    /**
     * 在 query 前应用待定设置
     * 比较 pending 和 lastApplied，按需调用 RPC 或 reconnect
     */
    async function applyPendingSettingsIfNeeded(): Promise<void> {
        if (!hasPendingSettings()) {
            log.debug(`[Tab ${tabId}] 无待应用设置`)
            return
        }

        log.info(`[Tab ${tabId}] 应用 pending 设置:`, pendingSettings.value)

        const pending = pendingSettings.value
        const lastApplied = lastAppliedSettings.value

        // 判断哪些设置需要更新
        const needsReconnect =
            (pending.thinkingEnabled !== undefined && pending.thinkingEnabled !== lastApplied.thinkingEnabled) ||
            (pending.skipPermissions !== undefined && pending.skipPermissions !== lastApplied.skipPermissions)

        const needsRpcUpdate =
            (pending.model !== undefined && pending.model !== lastApplied.model) ||
            (pending.permissionMode !== undefined && pending.permissionMode !== lastApplied.permissionMode)

        // 清空 pending（在应用之前清空，避免重复应用）
        pendingSettings.value = {}

        // 如果需要 reconnect，重连会应用所有设置
        if (needsReconnect) {
            log.info(`[Tab ${tabId}] 设置需要重连`)
            await reconnect({
                model: modelId.value || undefined,
                thinkingEnabled: thinkingEnabled.value,
                permissionMode: permissionMode.value,
                skipPermissions: skipPermissions.value
            })
            return
        }

        // 只需要 RPC 更新
        if (needsRpcUpdate) {
            log.info(`[Tab ${tabId}] 通过 RPC 应用设置`)
            if (pending.model !== undefined && pending.model !== lastApplied.model) {
                await setModel(pending.model)
            }
            if (pending.permissionMode !== undefined && pending.permissionMode !== lastApplied.permissionMode) {
                await setPermissionModeValue(pending.permissionMode)
            }
            // 更新 lastApplied
            updateLastAppliedSettings()
        }
    }

    /**
     * 更新 lastAppliedSettings 为当前设置
     */
    function updateLastAppliedSettings(): void {
        lastAppliedSettings.value = {
            model: modelId.value,
            permissionMode: permissionMode.value,
            thinkingEnabled: thinkingEnabled.value,
            skipPermissions: skipPermissions.value
        }
        log.debug(`[Tab ${tabId}] lastAppliedSettings 已更新:`, lastAppliedSettings.value)
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
        if (state.scrollPosition !== undefined) uiState.scrollPosition = state.scrollPosition
        if (state.newMessageCount !== undefined) uiState.newMessageCount = state.newMessageCount
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

        // 重置 UI 状态
        uiState.inputText = ''
        uiState.contexts = []
        uiState.scrollPosition = 0
        uiState.newMessageCount = 0

        // 重置错误状态
        connectionState.lastError = null

        log.debug(`[Tab ${tabId}] 已重置`)
    }

    // ========== 导出 ==========

    return {
        // Tab 标识
        tabId,

        // 基础信息（响应式）
        sessionId,
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
        thinkingEnabled,
        permissionMode,
        skipPermissions,
        resumeFromSessionId,

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

        // 消息相关（直接暴露 messagesHandler 的状态）
        messages: messagesHandler.messages,
        displayItems: messagesHandler.displayItems,
        messageQueue: messagesHandler.messageQueue,

        // 连接管理
        setInitialConnectOptions,
        connect,
        disconnect,
        reconnect,

        // 消息发送
        sendMessage,
        sendTextMessage,
        forceSendMessage,
        interrupt,

        // 队列管理
        editQueueMessage: messagesHandler.editQueueMessage,
        removeFromQueue: messagesHandler.removeFromQueue,
        clearQueue: messagesHandler.clearQueue,

        // 设置管理
        setModel,
        setPermissionMode: setPermissionModeValue,
        setLocalPermissionMode,
        updateSettings,

        // 设置延迟应用
        setPendingSetting,
        hasPendingSettings,
        applyPendingSettingsIfNeeded,
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
        loadMoreHistory
    }
}

/**
 * useSessionTab 返回类型
 */
export type SessionTabInstance = ReturnType<typeof useSessionTab>
