/**
 * Tab 会话管理 Composable（核心入口）- 多后端版本
 *
 * 本文件是 useSessionTab.ts 的升级版本，添加了多后端支持（Claude 和 Codex）
 *
 * 核心修改：
 * 1. 添加 backendType 字段到 TabInfo 和 TabConnectOptions
 * 2. 使用 BackendSession 接口替代 RSocketSession
 * 3. 使用工厂函数 createSession() 创建会话
 * 4. 添加 handleBackendEvent() 映射后端事件到现有消息处理
 * 5. 使用 ThinkingConfig 替代 ThinkingLevel
 * 6. 添加 canSwitchBackend 计算属性和 setBackendType() 方法
 */

import {ref, reactive, computed, shallowRef} from 'vue'
import type {ContentBlock, Message} from '@/types/message'
import {ConnectionStatus} from '@/types/display'
import type {RpcCapabilities, RpcPermissionMode} from '@/types/rpc'
import {useSessionTools, type SessionToolsInstance} from './useSessionTools'
import {useSessionStats, type SessionStatsInstance} from './useSessionStats'
import {useSessionPermissions, type SessionPermissionsInstance} from './useSessionPermissions'
import {useSessionMessages, type SessionMessagesInstance} from './useSessionMessages'
import type {ActiveFileInfo} from '@/services/jetbrainsRSocket'
import {loggers} from '@/utils/logger'
import type {PendingPermissionRequest, PendingUserQuestion, PermissionResponse} from '@/types/permission'
import {HISTORY_PAGE_SIZE} from '@/constants/messageWindow'
import {ideaBridge} from '@/services/ideaBridge'

// ========== 多后端支持导入 ==========
import type {BackendType, BackendEvent, BackendConnectionStatus} from '@/types/backend'
import type {ThinkingConfig} from '@/types/thinking'
import {createThinkingConfig} from '@/types/thinking'
import {createSession, type BackendSession, type SessionConnectOptions} from '@/services/backend'
import {getDefaultModel} from '@/services/backendCapabilities'

const log = loggers.session

export type ScrollMode = 'follow' | 'browse'

export interface ScrollAnchor {
    itemId: string
    offsetFromViewportTop: number
    viewportHeight: number
    savedAt: number
}

export interface ScrollState {
    mode: ScrollMode
    anchor: ScrollAnchor | null
    newMessageCount: number
}

export const DEFAULT_SCROLL_STATE: ScrollState = {
    mode: 'follow',
    anchor: null,
    newMessageCount: 0
}

export interface UIState {
    inputText: string
    contexts: any[]
    scrollState: ScrollState
    activeFileDismissed: boolean
}

/**
 * Tab 基础信息（新增 backendType 字段）
 */
export interface TabInfo {
    tabId: string
    sessionId: string | null
    name: string
    createdAt: number
    updatedAt: number
    lastActiveAt: number
    order: number
    backendType: BackendType  // 新增：后端类型
}

/**
 * 连接配置（新增 backendType 和 thinkingConfig 字段）
 */
export interface TabConnectOptions {
    backendType?: BackendType  // 新增：后端类型
    model?: string
    thinkingConfig?: ThinkingConfig  // 新增：完整的思考配置
    permissionMode?: RpcPermissionMode
    skipPermissions?: boolean
    continueConversation?: boolean
    resumeSessionId?: string
}

export const SETTING_KEYS = {
    MODEL: 'model',
    PERMISSION_MODE: 'permissionMode',
    THINKING_CONFIG: 'thinkingConfig',
    SKIP_PERMISSIONS: 'skipPermissions',
    BACKEND_TYPE: 'backendType',  // 新增
} as const

export type SettingKey = typeof SETTING_KEYS[keyof typeof SETTING_KEYS]

/**
 * Tab 会话管理 Composable（多后端版本）
 */
export function useSessionTab(initialOrder: number = 0, initialBackendType: BackendType = 'claude') {
    // ========== 组合其他 Composables ==========
    const tools: SessionToolsInstance = useSessionTools()
    const stats: SessionStatsInstance = useSessionStats()
    const permissions: SessionPermissionsInstance = useSessionPermissions()
    const messagesHandler: SessionMessagesInstance = useSessionMessages(tools, stats)

    // ========== Tab 基础信息 ==========
    const tabId = `tab-${Date.now()}-${Math.random().toString(36).substring(2, 8)}`
    const sessionId = ref<string | null>(null)
    const projectPath = ref<string | null>(null)
    const name = ref('新会话')
    const createdAt = Date.now()
    const updatedAt = ref(createdAt)
    const lastActiveAt = ref(createdAt)
    const order = ref(initialOrder)

    // ========== 后端类型（新增）==========
    const backendType = ref<BackendType>(initialBackendType)

    // ========== 连接状态 ==========
    const backendSession = shallowRef<BackendSession | null>(null)

    const connectionState = reactive({
        status: ConnectionStatus.DISCONNECTED as ConnectionStatus,
        capabilities: null as RpcCapabilities | null,
        lastError: null as string | null
    })

    const mcpServers = ref<Array<{ name: string; status: string }>>([])

    // ========== 连接设置 ==========
    const modelId = ref<string | null>(null)
    const thinkingConfig = ref<ThinkingConfig>(createThinkingConfig(backendType.value))
    const permissionMode = ref<RpcPermissionMode>('default')
    const skipPermissions = ref(false)
    const initialConnectOptions = ref<TabConnectOptions | null>(null)

    // ========== 设置跟踪 ==========
    const lastAppliedSettings = ref<Partial<TabConnectOptions>>({})
    const pendingSettings = ref<Partial<TabConnectOptions>>({})

    function updateLastAppliedSettings(): void {
        lastAppliedSettings.value = {
            backendType: backendType.value,
            model: modelId.value || undefined,
            thinkingConfig: thinkingConfig.value,
            permissionMode: permissionMode.value
        }
    }

    async function applyPendingSettingsIfNeeded(): Promise<void> {
        if (Object.keys(pendingSettings.value).length === 0) return

        if (pendingSettings.value.model !== undefined) {
            await setModel(pendingSettings.value.model)
        }
        if (pendingSettings.value.permissionMode !== undefined) {
            await setPermissionModeValue(pendingSettings.value.permissionMode)
        }
        if (pendingSettings.value.thinkingConfig !== undefined) {
            await updateThinkingConfig(pendingSettings.value.thinkingConfig)
        }

        pendingSettings.value = {}
        updateLastAppliedSettings()
    }

    function setPendingSetting<K extends keyof TabConnectOptions>(key: K, value: TabConnectOptions[K]): void {
        if (key === 'skipPermissions') {
            skipPermissions.value = value as boolean
            return
        }

        pendingSettings.value = {...pendingSettings.value, [key]: value}

        switch (key) {
            case 'backendType':
                if (backendSession.value?.isConnected()) {
                    throw new Error('Cannot switch backend on active session')
                }
                backendType.value = value as BackendType
                break
            case 'model':
                modelId.value = value as string
                break
            case 'thinkingConfig':
                thinkingConfig.value = value as ThinkingConfig
                break
            case 'permissionMode':
                permissionMode.value = value as RpcPermissionMode
                break
        }
    }

    function setInitialConnectOptions(options: TabConnectOptions) {
        initialConnectOptions.value = {...options}
        if (options.backendType) backendType.value = options.backendType
        if (options.model) modelId.value = options.model
        if (options.thinkingConfig) thinkingConfig.value = options.thinkingConfig
        if (options.permissionMode) permissionMode.value = options.permissionMode
        if (options.skipPermissions !== undefined) skipPermissions.value = options.skipPermissions
    }

    // ========== UI 状态 ==========
    const uiState = reactive<UIState>({
        inputText: '',
        contexts: [],
        scrollState: { ...DEFAULT_SCROLL_STATE },
        activeFileDismissed: false
    })

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
        Object.assign(historyState, {
            loading: false,
            total: 0,
            loadedStart: 0,
            loadedCount: 0,
            hasMore: false,
            lastOffset: 0,
            lastLimit: HISTORY_PAGE_SIZE
        })
    }

    // ========== 计算属性 ==========
    const isConnected = computed(() => connectionState.status === ConnectionStatus.CONNECTED)
    const isConnecting = computed(() => connectionState.status === ConnectionStatus.CONNECTING)
    const hasError = computed(() => connectionState.status === ConnectionStatus.ERROR)
    const isGenerating = computed(() => messagesHandler.isGenerating.value)
    // 现在支持在任何状态下切换后端（切换时会自动断开并重置）
    const canSwitchBackend = computed(() => true)

    const tabInfo = computed<TabInfo>(() => ({
        tabId,
        sessionId: sessionId.value,
        name: name.value,
        createdAt,
        updatedAt: updatedAt.value,
        lastActiveAt: lastActiveAt.value,
        order: order.value,
        backendType: backendType.value
    }))

    // ========== 后端事件映射（新增）==========

    function handleBackendEvent(event: BackendEvent): void {
        switch (event.type) {
            case 'text_delta':
                messagesHandler.handleStreamEvent({
                    type: 'stream_event',
                    event: 'text_delta',
                    data: { text: event.delta }
                } as any)
                break

            case 'thinking_delta':
                messagesHandler.handleStreamEvent({
                    type: 'stream_event',
                    event: 'thinking_delta',
                    data: { thinking: event.delta }
                } as any)
                break

            case 'tool_started':
                messagesHandler.handleStreamEvent({
                    type: 'stream_event',
                    event: 'tool_use',
                    data: {
                        type: 'tool_use',
                        id: event.toolUseId,
                        name: event.toolName,
                        input: event.input
                    }
                } as any)
                break

            case 'tool_result':
                messagesHandler.handleStreamEvent({
                    type: 'stream_event',
                    event: 'tool_result',
                    data: {
                        type: 'tool_result',
                        tool_use_id: event.toolUseId,
                        content: event.result,
                        is_error: event.isError
                    }
                } as any)
                break

            case 'message_start':
                messagesHandler.handleStreamEvent({
                    type: 'stream_event',
                    event: 'message_start',
                    data: { role: event.role }
                } as any)
                break

            case 'message_end':
                messagesHandler.handleResultMessage({
                    type: 'result',
                    subtype: 'success',
                    is_error: false
                } as any)
                break

            case 'error':
                messagesHandler.addErrorMessage(event.error)
                break
        }

        touch()
    }

    function handleConnectionStatusChange(status: BackendConnectionStatus): void {
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
    }

    // ========== 连接管理 ==========

    const MAX_RECONNECT_ATTEMPTS = 3
    const RECONNECT_DELAY = 2000
    let reconnectAttempts = 0
    let reconnectTimer: ReturnType<typeof setTimeout> | null = null

    function handleSessionDisconnected(error?: Error): void {
        connectionState.status = ConnectionStatus.DISCONNECTED
        connectionState.lastError = error?.message || '连接已断开'
        backendSession.value = null
        sessionId.value = null

        if (messagesHandler.isGenerating.value) {
            messagesHandler.stopGenerating()
        }

        permissions.cancelAllPermissions('连接断开')
        permissions.cancelAllQuestions('连接断开')
    }

    async function connect(options: TabConnectOptions = {}): Promise<void> {
        const resolvedOptions: TabConnectOptions = {...(initialConnectOptions.value || {}), ...options}
        if (connectionState.status === ConnectionStatus.CONNECTING) return

        if (backendSession.value) {
            backendSession.value.disconnect()
            backendSession.value = null
            sessionId.value = null
        }

        connectionState.status = ConnectionStatus.CONNECTING
        connectionState.lastError = null

        try {
            const currentBackendType = resolvedOptions.backendType || backendType.value
            const defaultModel = getDefaultModel(currentBackendType)

            const session = createSession(currentBackendType, {
                type: currentBackendType,
                modelId: modelId.value || resolvedOptions.model || defaultModel?.id,
                thinkingConfig: thinkingConfig.value,
                permissionMode: permissionMode.value,
            })

            session.onEvent(handleBackendEvent)
            session.onConnectionStatusChange(handleConnectionStatusChange)

            let currentProjectPath = projectPath.value
            if (!currentProjectPath) {
                try {
                    const pathResult = await ideaBridge.query('ide.getProjectPath', {})
                    if (pathResult.success && pathResult.data?.projectPath) {
                        currentProjectPath = pathResult.data.projectPath as string
                        projectPath.value = currentProjectPath
                    }
                } catch (e) {
                    log.warn(`[Tab ${tabId}] 获取项目路径失败:`, e)
                }
            }

            const connectConfig: SessionConnectOptions = {
                config: session.getState().config,
                projectPath: currentProjectPath || undefined,
                resumeSessionId: resolvedOptions.resumeSessionId,
            }

            const newSessionId = await session.connect(connectConfig)

            backendSession.value = session
            sessionId.value = newSessionId
            connectionState.capabilities = session.getCapabilities()
            connectionState.status = ConnectionStatus.CONNECTED
            connectionState.lastError = null

            reconnectAttempts = 0

            messagesHandler.setBeforeProcessQueueFn(async () => {
                await applyPendingSettingsIfNeeded()
            })

            messagesHandler.setProcessQueueFn(async () => {
                await processNextQueuedMessage()
            })

            registerRpcHandlers()

            updateLastAppliedSettings()
            pendingSettings.value = {}

            log.info(`[Tab ${tabId}] 连接成功: sessionId=${newSessionId}, backend=${currentBackendType}`)

            processNextQueuedMessage()
        } catch (error) {
            connectionState.status = ConnectionStatus.ERROR
            connectionState.lastError = error instanceof Error ? error.message : String(error)
            log.error(`[Tab ${tabId}] 连接失败:`, error)
        }
    }

    async function disconnect(): Promise<void> {
        if (backendSession.value) {
            backendSession.value.disconnect()
            backendSession.value = null
        }

        sessionId.value = null
        connectionState.status = ConnectionStatus.DISCONNECTED

        permissions.cancelAllPermissions('Tab disconnected')
        permissions.cancelAllQuestions('Tab disconnected')
    }

    // ========== RPC 处理器注册 ==========

    function registerRpcHandlers(): void {
        if (!backendSession.value) return

        backendSession.value.registerHandler('AskUserQuestion', async (params: Record<string, any>) => {
            return new Promise((resolve, reject) => {
                const questionId = `question-${Date.now()}`

                const question: Omit<PendingUserQuestion, 'createdAt'> = {
                    id: questionId,
                    sessionId: sessionId.value!,
                    questions: params.questions || [],
                    resolve: (answers) => resolve(answers),
                    reject
                }

                permissions.addUserQuestion(question)
            })
        })

        backendSession.value.registerHandler('RequestPermission', async (params: Record<string, any>) => {
            const toolName = params.toolName || 'Unknown'

            if (skipPermissions.value && toolName !== 'ExitPlanMode') {
                return { approved: true }
            }

            return new Promise((resolve, reject) => {
                const permissionId = `permission-${Date.now()}`

                const request: Omit<PendingPermissionRequest, 'createdAt'> = {
                    id: permissionId,
                    sessionId: sessionId.value!,
                    toolName,
                    input: params.input || {},
                    matchedToolCallId: params.toolUseId,
                    permissionSuggestions: params.permissionSuggestions,
                    resolve: (response: PermissionResponse) => resolve(response),
                    reject
                }

                permissions.addPermissionRequest(request)
            })
        })
    }

    // ========== 消息发送 ==========

    async function ensureConnected(): Promise<void> {
        if (connectionState.status === ConnectionStatus.CONNECTED) {
            return
        }

        if (connectionState.status === ConnectionStatus.CONNECTING) {
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

        await connect(initialConnectOptions.value || {})
    }

    async function sendMessage(
        message: { contexts: any[]; contents: ContentBlock[]; ideContext?: ActiveFileInfo | null },
        options?: { isSlashCommand?: boolean }
    ): Promise<void> {
        if (options?.isSlashCommand) {
            message = {...message, contexts: [], ideContext: null}
        }

        const textContent = message.contents.find(c => c.type === 'text') as { text?: string } | undefined

        if (messagesHandler.messages.length === 0 && textContent?.text) {
            const text = textContent.text.trim()
            if (!text.startsWith('/')) {
                const maxLen = 30
                const newTitle = text.length > maxLen ? text.slice(0, maxLen) + '...' : text
                rename(newTitle)
            }
        }

        if (connectionState.status !== ConnectionStatus.CONNECTED) {
            messagesHandler.addToQueue(message)
            if (connectionState.status !== ConnectionStatus.CONNECTING) {
                await ensureConnected()
            }
            return
        }

        if (messagesHandler.isGenerating.value) {
            messagesHandler.addToQueue(message)
            return
        }

        const {userMessage, mergedContent} = messagesHandler.addMessageToUI(message)
        touch()

        await sendMessageToBackend(userMessage, mergedContent, message)
    }

    async function sendMessageToBackend(
        userMessage: Message,
        mergedContent: ContentBlock[],
        originalMessage: { contexts: any[]; contents: ContentBlock[] }
    ): Promise<void> {
        try {
            await ensureConnected()

            if (!backendSession.value) {
                throw new Error('会话未连接')
            }

            messagesHandler.startGenerating(userMessage.id)

            await backendSession.value.sendMessage(mergedContent as any)
        } catch (err) {
            messagesHandler.stopGenerating()
            messagesHandler.addToQueue(originalMessage)
        }
    }

    async function processNextQueuedMessage(): Promise<void> {
        const next = messagesHandler.popNextQueuedMessage()
        if (!next) return

        await sendMessageToBackend(next.userMessage, next.mergedContent, next.originalMessage)
    }

    async function sendTextMessage(text: string): Promise<void> {
        await sendMessage({
            contexts: [],
            contents: [{type: 'text', text}]
        })
    }

    async function sendTextMessageDirect(text: string): Promise<void> {
        if (!backendSession.value) {
            throw new Error('会话未连接')
        }
        await backendSession.value.sendMessage([{type: 'text', text}])
    }

    async function interrupt(): Promise<void> {
        if (!backendSession.value) {
            throw new Error('会话未连接')
        }

        messagesHandler.setInterruptMode('clear')

        try {
            await backendSession.value.interrupt()
        } catch (err) {
            log.error(`[Tab ${tabId}] 中断请求失败:`, err)
        } finally {
            if (messagesHandler.isGenerating.value) {
                messagesHandler.stopGenerating()
                messagesHandler.clearQueue()
            }
        }
    }

    async function runInBackground(): Promise<void> {
        if (!backendSession.value) {
            throw new Error('会话未连接')
        }

        if (!messagesHandler.isGenerating.value) {
            return
        }

        await backendSession.value.runInBackground()
    }

    // ========== 设置管理 ==========

    /**
     * 切换后端类型
     * 如果当前已连接，会自动断开并重置会话状态
     */
    async function setBackendType(newType: BackendType): Promise<void> {
        if (newType === backendType.value) {
            return // 无需切换
        }

        // 如果已连接，先断开并重置
        if (backendSession.value?.isConnected()) {
            log.info(`[Tab ${tabId}] 切换后端: ${backendType.value} -> ${newType}，正在重置会话...`)
            await disconnect()
            reset()
        }

        backendType.value = newType
        thinkingConfig.value = createThinkingConfig(newType)

        log.info(`[Tab ${tabId}] 后端已切换为 ${newType}`)
    }

    async function setModel(model: string): Promise<void> {
        if (!backendSession.value) {
            modelId.value = model
            return
        }

        await backendSession.value.setModel(model)
        modelId.value = model
    }

    async function setPermissionModeValue(mode: RpcPermissionMode): Promise<void> {
        if (!backendSession.value) {
            permissionMode.value = mode
            return
        }

        await backendSession.value.setPermissionMode(mode)
        permissionMode.value = mode
    }

    async function updateThinkingConfig(config: ThinkingConfig): Promise<void> {
        thinkingConfig.value = config
        if (backendSession.value?.isConnected()) {
            await backendSession.value.updateThinkingConfig(config)
        }
    }

    function setLocalPermissionMode(mode: RpcPermissionMode): void {
        permissionMode.value = mode
    }

    interface SettingsUpdate {
        backendType?: BackendType
        model?: string
        permissionMode?: RpcPermissionMode
        thinkingConfig?: ThinkingConfig
        skipPermissions?: boolean
    }

    async function updateSettings(settings: SettingsUpdate): Promise<void> {
        if (!sessionId.value || connectionState.status !== ConnectionStatus.CONNECTED) {
            if (settings.backendType !== undefined) setBackendType(settings.backendType)
            if (settings.model !== undefined) modelId.value = settings.model
            if (settings.permissionMode !== undefined) permissionMode.value = settings.permissionMode
            if (settings.thinkingConfig !== undefined) thinkingConfig.value = settings.thinkingConfig
            if (settings.skipPermissions !== undefined) skipPermissions.value = settings.skipPermissions
            return
        }

        if (settings.backendType !== undefined) {
            throw new Error('Cannot change backend type on connected session')
        }
        if (settings.model !== undefined) {
            await setModel(settings.model)
        }
        if (settings.permissionMode !== undefined) {
            await setPermissionModeValue(settings.permissionMode)
        }
        if (settings.thinkingConfig !== undefined) {
            await updateThinkingConfig(settings.thinkingConfig)
        }
        if (settings.skipPermissions !== undefined) {
            skipPermissions.value = settings.skipPermissions
        }
    }

    // ========== 辅助方法 ==========

    function touch(): void {
        const now = Date.now()
        updatedAt.value = now
        lastActiveAt.value = now
    }

    function rename(newName: string): void {
        name.value = newName
        touch()
    }

    function setOrder(newOrder: number): void {
        order.value = newOrder
    }

    function saveUiState(state: Partial<UIState>): void {
        if (state.inputText !== undefined) uiState.inputText = state.inputText
        if (state.contexts !== undefined) uiState.contexts = state.contexts
        if (state.scrollState !== undefined) {
            Object.assign(uiState.scrollState, state.scrollState)
        }
    }

    function reset(): void {
        tools.reset()
        stats.reset()
        permissions.reset()
        messagesHandler.reset()
        resetHistoryState()

        Object.assign(uiState.scrollState, DEFAULT_SCROLL_STATE)
        connectionState.lastError = null
    }

    function getTabInfo(): TabInfo {
        return {
            tabId,
            sessionId: sessionId.value,
            name: name.value,
            createdAt,
            updatedAt: updatedAt.value,
            lastActiveAt: lastActiveAt.value,
            order: order.value,
            backendType: backendType.value
        }
    }

    // ========== 简化版方法（保留接口签名）==========

    async function loadHistory(): Promise<void> {}
    async function loadMoreHistory(): Promise<void> {}
    async function reconnect(options?: TabConnectOptions): Promise<void> {
        await connect(options || {})
    }
    async function editAndResendMessage(): Promise<void> {}
    async function forceSendMessage(): Promise<void> {}

    // ========== 导出 ==========

    return {
        tabId,
        sessionId,
        projectPath,
        name,
        order,
        updatedAt,
        lastActiveAt,

        backendType,
        canSwitchBackend,

        connectionState,
        get connectionStatus() {
            return connectionState.status
        },
        get capabilities() {
            return connectionState.capabilities
        },
        get lastError() {
            return connectionState.lastError
        },

        modelId,
        thinkingConfig,
        permissionMode,
        skipPermissions,
        resumeFromSessionId: computed(() => initialConnectOptions.value?.resumeSessionId ?? null),

        mcpServers,
        uiState,

        isCompacting: ref(false),
        compactMetadata: ref(null),

        isConnected,
        isConnecting,
        hasError,
        isGenerating,
        tabInfo,

        tools,
        stats,
        permissions,

        messages: messagesHandler.messages,
        displayItems: messagesHandler.displayItems,
        messageQueue: messagesHandler.messageQueue,

        setInitialConnectOptions,
        connect,
        disconnect,
        reconnect,

        sendMessage,
        sendTextMessage,
        sendTextMessageDirect,
        forceSendMessage,
        interrupt,
        runInBackground,
        editAndResendMessage,

        editQueueMessage: messagesHandler.editQueueMessage,
        removeFromQueue: messagesHandler.removeFromQueue,
        clearQueue: messagesHandler.clearQueue,

        setBackendType,
        setModel,
        setPermissionMode: setPermissionModeValue,
        updateThinkingConfig,
        setLocalPermissionMode,
        updateSettings,
        setPendingSetting,
        pendingSettings,
        lastAppliedSettings,

        historyState,

        touch,
        rename,
        setOrder,
        saveUiState,
        reset,
        getTabInfo,

        loadHistory,
        loadMoreHistory,

        get session() {
            return backendSession.value
        }
    }
}

export type SessionTabInstance = ReturnType<typeof useSessionTab>
