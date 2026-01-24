/**
 * AI Agent 服务
 *
 * 重构后的版本：只保留 HTTP API 方法。
 * RSocket 会话管理已移至 useSessionTab（每个 Tab 直接持有 RSocketSession 实例）。
 */

import {ProtoCodec} from './rsocket/protoCodec'
import type {RSocketSession} from './rsocket/RSocketSession'
import type {HistorySessionMetadata} from '@/types/session'
import type {RpcMessage} from '@/types/rpc'
import {resolveServerHttpUrl} from '@/utils/serverUrl'
import { withServerToken } from '@/utils/serverAuth'

// 重新导出类型以保持向后兼容
export type {ConnectOptions} from './rsocket/RSocketSession'

/** 历史文件元数据 */
export interface HistoryMetadata {
    totalLines: number      // JSONL 文件总行数
    sessionId: string       // 会话 ID
    projectPath: string     // 项目路径
    customTitle?: string    // 自定义标题（从 /rename 命令设置）
}

/**
 * AI Agent HTTP 服务
 *
 * 注意：RSocket 会话相关操作已移至 useSessionTab，
 * 此服务只负责纯 HTTP API 调用（历史记录加载等）。
 */
export class AiAgentService {

    /**
     * 获取项目的历史会话列表（通过 HTTP，避免 RSocket 连接）
     *
     * @param maxResults 最大结果数（默认 50）
     * @returns 历史会话列表
     */
    async getHistorySessions(
        maxResults: number = 50,
        offset: number = 0,
        provider?: string
    ): Promise<HistorySessionMetadata[]> {
        try {
            console.log(`📋 [HTTP] 获取历史会话列表 (offset=${offset}, maxResults=${maxResults})`)

            // 使用 HTTP 调用（不依赖 RSocket 连接）
            const baseUrl = resolveServerHttpUrl()
            const providerQuery = provider ? `&provider=${encodeURIComponent(provider)}` : ''
            const url = `${baseUrl}/api/history/sessions?offset=${offset}&maxResults=${maxResults}${providerQuery}`

            const response = await fetch(url)
            if (!response.ok) {
                throw new Error(`HTTP ${response.status}: ${response.statusText}`)
            }

            const result = await response.json()
            // 调试日志：打印后端返回的原始数据
            console.log('📋 [HTTP] 后端返回的历史会话原始数据:', JSON.stringify(result, null, 2))
            return result.sessions || []
        } catch (error) {
            console.warn('[aiAgentService] 获取历史会话列表失败:', error)
            return []
        }
    }

    /**
     * 加载历史消息（非流式，一次性返回结果）
     */
    async loadHistory(
        params: { sessionId?: string; projectPath?: string; offset?: number; limit?: number },
        provider?: string
    ): Promise<{ messages: RpcMessage[]; offset: number; count: number; availableCount: number }> {
        console.log('📜 [AiAgentService] 加载历史 (HTTP protobuf):', params)

        const baseUrl = resolveServerHttpUrl()
        const providerQuery = provider ? `?provider=${encodeURIComponent(provider)}` : ''
        const url = `${baseUrl}/api/history/load.pb${providerQuery}`

        const body = ProtoCodec.encodeLoadHistoryRequest({
            sessionId: params.sessionId,
            projectPath: params.projectPath,
            offset: params.offset,
            limit: params.limit
        })

        const response = await fetch(url, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/octet-stream'
            },
            body
        })

        if (!response.ok) {
            throw new Error(`HTTP ${response.status}: ${response.statusText}`)
        }

        const buffer = new Uint8Array(await response.arrayBuffer())
        // 类型断言解决不同路径导入 RpcMessage 类型不兼容的问题
        return ProtoCodec.decodeHistoryResult(buffer) as any
    }

    /**
     * 加载子代理历史消息
     *
     * @param agentId 子代理 ID（如 "afd66ee"）
     * @param projectPath 项目路径（用于定位历史文件目录）
     * @returns 子代理的历史消息列表
     */
    async loadSubagentHistory(
        agentId: string,
        projectPath: string
    ): Promise<RpcMessage[]> {
        console.log('🔄 [AiAgentService] 加载子代理历史:', {agentId, projectPath})

        // 子代理的 sessionId 格式为 agent-{agentId}
        const result = await this.loadHistory({
            sessionId: `agent-${agentId}`,
            projectPath
        })

        return result.messages
    }

    /**
     * 删除历史会话（删除 JSONL 文件）
     *
     * @param sessionId 会话 ID
     * @returns 删除结果
     */
    async deleteHistorySession(
        sessionId: string,
        provider?: string
    ): Promise<{ success: boolean; error?: string }> {
        try {
            console.log(`🗑️ [HTTP] 删除历史会话: ${sessionId}`)

            const baseUrl = resolveServerHttpUrl()
            const providerQuery = provider ? `?provider=${encodeURIComponent(provider)}` : ''
            const url = `${baseUrl}/api/history/sessions/${encodeURIComponent(sessionId)}${providerQuery}`

            const response = await fetch(url, {
                method: 'DELETE'
            })

            if (!response.ok) {
                const errorData = await response.json().catch(() => ({}))
                throw new Error(errorData.error || `HTTP ${response.status}: ${response.statusText}`)
            }

            const result = await response.json()
            console.log('🗑️ [HTTP] 删除结果:', result)
            return result
        } catch (error) {
            console.error('[aiAgentService] 删除历史会话失败:', error)
            return { success: false, error: error instanceof Error ? error.message : 'Unknown error' }
        }
    }

    /**
     * 获取历史文件元数据（文件总行数等）
     *
     * @param params 查询参数
     * @returns 历史文件元数据
     */
    async getHistoryMetadata(
        params: { sessionId?: string; projectPath?: string },
        provider?: string
    ): Promise<HistoryMetadata> {
        console.log('📊 [AiAgentService] 获取历史元数据 (HTTP protobuf):', params)

        const baseUrl = resolveServerHttpUrl()
        const providerQuery = provider ? `?provider=${encodeURIComponent(provider)}` : ''
        const url = `${baseUrl}/api/history/metadata.pb${providerQuery}`

        const body = ProtoCodec.encodeGetHistoryMetadataRequest({
            sessionId: params.sessionId,
            projectPath: params.projectPath
        })

        const response = await fetch(url, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/octet-stream'
            },
            body
        })

        if (!response.ok) {
            throw new Error(`HTTP ${response.status}: ${response.statusText}`)
        }

        const buffer = new Uint8Array(await response.arrayBuffer())
        const meta = ProtoCodec.decodeHistoryMetadata(buffer)
        return {
            totalLines: meta.totalLines,
            sessionId: meta.sessionId,
            projectPath: meta.projectPath,
            customTitle: meta.customTitle
        }
    }

    /**
     * 截断历史记录（用于编辑重发功能）
     *
     * 从指定的消息 UUID 开始截断 JSONL 历史文件，该消息及其后续所有消息都会被删除。
     * 这是一个文件操作，不依赖于特定会话状态，但需要一个已连接的 RSocket 连接来发送请求。
     *
     * @param session 任意已连接的 RSocketSession 实例（用于发送 RSocket 请求）
     * @param params 截断参数
     * @param params.sessionId 目标会话 ID（历史文件标识）
     * @param params.messageUuid 要截断的消息 UUID（从该消息开始截断，包含该消息）
     * @param params.projectPath 项目路径（用于定位 JSONL 文件）
     * @returns 截断结果
     */
    async truncateHistory(
        session: RSocketSession,
        params: {
            sessionId: string
            messageUuid: string
            projectPath: string
        }
    ): Promise<{ success: boolean; remainingLines: number; error?: string }> {
        console.log('✂️ [AiAgentService] 截断历史:', params)

        if (!session.isConnected) {
            throw new Error('RSocket 连接未建立')
        }

        return await session.truncateHistory(params)
    }

    /**
     * 检查是否在 IDE 环境中运行
     *
     * - ai-agent-server (默认): 返回 false
     * - jetbrains-plugin (IDEA): 返回 true
     *
     * 前端根据此值决定是否连接 jetbrains-rsocket 获取 IDE 设置
     */
    async hasIdeEnvironment(): Promise<boolean> {
        try {
            const baseUrl = resolveServerHttpUrl()
            const url = `${baseUrl}/api/`

            const response = await fetch(url, {
                method: 'POST',
                headers: withServerToken({
                    'Content-Type': 'application/json'
                }),
                body: JSON.stringify({ action: 'ide.hasIdeEnvironment' })
            })

            if (!response.ok) {
                console.warn('[aiAgentService] hasIdeEnvironment 请求失败:', response.status)
                return false
            }

            const result = await response.json()
            const hasIde = result.data?.hasIde ?? false
            console.log('🖥️ [aiAgentService] hasIdeEnvironment:', hasIde)
            return hasIde
        } catch (error) {
            console.warn('[aiAgentService] hasIdeEnvironment 请求异常:', error)
            return false
        }
    }
}

// 导出单例
export const aiAgentService = new AiAgentService()
