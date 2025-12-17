<template>
  <CompactToolCard
    :display-info="displayInfoWithAgent"
    :is-expanded="expanded"
    :has-details="hasDetails"
    :supports-background="!isSubagent"
    @click="expanded = !expanded"
  >
    <template #details>
      <div class="task-details">
        <!-- 1. 参数区域（提示词） -->
        <!-- 注意：Desc 和 Model 已在折叠状态的标题行显示，展开后不再重复显示 -->
        <div class="params-section">
          <div v-if="prompt" class="prompt-section">
            <div class="section-header-row">
              <div class="section-title">Prompt</div>
              <button class="copy-btn" @click.stop="copyPrompt" :title="promptCopied ? '已复制' : '复制提示词'">
                {{ promptCopied ? '✓' : '📋' }}
              </button>
            </div>
            <div class="prompt-content">
              <MarkdownRenderer :content="prompt" />
            </div>
          </div>
        </div>

        <!-- 2. 子代理调用过程（可折叠） -->
        <div v-if="subagentMessages.length > 0 || subagentHistoryLoading" class="subagent-section">
          <div class="section-header" @click.stop="processExpanded = !processExpanded">
            <span class="expand-icon">{{ processExpanded ? '▼' : '▶' }}</span>
            <span class="section-title">Process</span>
<span class="item-count">({{ subagentMessages.length }})</span>
          </div>
          <div v-if="processExpanded" class="subagent-container">
            <div v-if="subagentHistoryLoading" class="loading-hint">Loading...</div>
            <div v-else class="subagent-list">
              <DisplayItemRenderer
                v-for="item in subagentMessages"
                :key="item.id"
                :source="item"
              />
            </div>
          </div>
        </div>

        <!-- 3. 结果区域 -->
        <div v-if="hasResult" class="result-section">
          <div class="section-header-row">
            <div class="section-title">Result</div>
            <button class="copy-btn" @click.stop="copyResult" :title="resultCopied ? '已复制' : '复制结果'">
              {{ resultCopied ? '✓' : '📋' }}
            </button>
          </div>
          <div class="result-content">
            <MarkdownRenderer :content="resultText" />
          </div>
        </div>
      </div>
    </template>
  </CompactToolCard>
</template>

<script setup lang="ts">
import { ref, computed, inject, watch, type ComputedRef } from 'vue'
import type { GenericToolCall, ToolCall } from '@/types/display'
import type { AiAgentService } from '@/services/aiAgentService'
import type { RpcMessage } from '@/types/rpc'
import { mapRpcMessageToMessage } from '@/utils/rpcMappers'
import { convertMessageToDisplayItems } from '@/utils/displayItemConverter'
import CompactToolCard from './CompactToolCard.vue'
import { extractToolDisplayInfo } from '@/utils/toolDisplayInfo'
import DisplayItemRenderer from '@/components/chat/DisplayItemRenderer.vue'
import MarkdownRenderer from '@/components/markdown/MarkdownRenderer.vue'

interface Props {
  toolCall: GenericToolCall
}

const props = defineProps<Props>()
// Task 默认折叠
const expanded = ref(false)
// 子代理调用过程默认展开
const processExpanded = ref(true)

// 判断是否是子代理的 Task 调用（子代理不显示 run in background 提示）
const isSubagent = computed(() => !!(props.toolCall as any).parentToolUseId)

// 复制状态
const promptCopied = ref(false)
const resultCopied = ref(false)

// 复制提示词
async function copyPrompt() {
  if (!prompt.value) return
  try {
    await navigator.clipboard.writeText(prompt.value)
    promptCopied.value = true
    setTimeout(() => { promptCopied.value = false }, 2000)
  } catch (e) {
    console.error('复制失败:', e)
  }
}

// 复制结果
async function copyResult() {
  if (!resultText.value) return
  try {
    await navigator.clipboard.writeText(resultText.value)
    resultCopied.value = true
    setTimeout(() => { resultCopied.value = false }, 2000)
  } catch (e) {
    console.error('复制失败:', e)
  }
}

// 从上下文获取 projectPath 和 aiAgentService
const projectPath = inject<ComputedRef<string>>('projectPath')
const aiAgentService = inject<AiAgentService>('aiAgentService')

// 子代理历史加载状态
const subagentHistoryLoaded = ref(false)
const subagentHistoryLoading = ref(false)

// 获取 agentId（从 toolCall 中解析）
const agentId = computed(() => (props.toolCall as any).agentId as string | undefined)
const toolUseId = computed(() => props.toolCall.id)

/**
 * 加载子代理历史消息
 * - 只有历史会话中的 Task 才需要加载（流式时子代理消息已经实时推送）
 * - 加载后给每条消息加上 parentToolUseId，复用现有的子代理消息处理逻辑
 */
async function loadSubagentHistory() {
  // 前置检查
  if (!agentId.value || !toolUseId.value) {
    console.log('[TaskToolDisplay] 缺少 agentId 或 toolUseId，跳过加载')
    return
  }
  if (subagentHistoryLoaded.value || subagentHistoryLoading.value) {
    return
  }
  if (!projectPath?.value || !aiAgentService) {
    console.warn('[TaskToolDisplay] 缺少 projectPath 或 aiAgentService', {
      projectPath: projectPath?.value,
      hasAiAgentService: !!aiAgentService
    })
    return
  }
  // 如果已有子代理消息（流式推送的），不需要加载历史
  const existingMessages = (props.toolCall as any).subagentMessages || []
  if (existingMessages.length > 0) {
    subagentHistoryLoaded.value = true
    return
  }

  console.log('[TaskToolDisplay] 开始加载子代理历史:', { agentId: agentId.value, projectPath: projectPath.value })
  subagentHistoryLoading.value = true

  try {
    // 调用 API 加载子代理历史
    const messages = await aiAgentService.loadSubagentHistory(agentId.value, projectPath.value)
    console.log('[TaskToolDisplay] 加载到子代理消息:', messages.length)

    // 给每条消息加上 parentToolUseId（= Task 的 toolUseId）
    // 然后转换为 DisplayItem 并追加到 subagentMessages
    const pendingToolCalls = new Map<string, ToolCall>()

    for (const rpcMsg of messages) {
      // 给消息加上 parentToolUseId
      (rpcMsg as any).parentToolUseId = toolUseId.value

      // 转换为 Message 格式
      const message = mapRpcMessageToMessage(rpcMsg as any)
      if (!message) continue

      // 转换为 DisplayItem
      const displayBatch = convertMessageToDisplayItems(message, pendingToolCalls)
      // 过滤掉 userMessage（子代理的 prompt 已在 Task 参数中显示）
      const filteredBatch = displayBatch.filter(item => item.displayType !== 'userMessage')
      if (filteredBatch.length > 0) {
        // 追加到 subagentMessages
        if (!(props.toolCall as any).subagentMessages) {
          (props.toolCall as any).subagentMessages = []
        }
        (props.toolCall as any).subagentMessages.push(...filteredBatch)
      }
    }

    subagentHistoryLoaded.value = true
    console.log('[TaskToolDisplay] 子代理历史加载完成，共:', (props.toolCall as any).subagentMessages?.length || 0, '项')
  } catch (error) {
    console.error('[TaskToolDisplay] 加载子代理历史失败:', error)
  } finally {
    subagentHistoryLoading.value = false
  }
}

// 监听展开状态和 projectPath，展开时加载子代理历史
// projectPath 可能在组件初始化时还没有准备好，所以需要监听它的变化
watch(
  [expanded, () => projectPath?.value],
  ([isExpanded, path]) => {
    console.log('[TaskToolDisplay] watch 触发:', {
      isExpanded,
      agentId: agentId.value,
      path,
      subagentHistoryLoaded: subagentHistoryLoaded.value,
      toolCallId: props.toolCall.id,
      hasResult: !!props.toolCall.result
    })
    if (isExpanded && agentId.value && path && !subagentHistoryLoaded.value) {
      loadSubagentHistory()
    }
  },
  { immediate: true }
)

const displayInfo = computed(() => extractToolDisplayInfo(props.toolCall as any, props.toolCall.result as any))
const agentName = computed(() => (props.toolCall as any).agentName || props.toolCall.input?.subagent_type || '')
const displayInfoWithAgent = computed(() => {
  const info = displayInfo.value
  // 折叠时显示 Desc 和 Model 信息
  if (!expanded.value) {
    const parts: string[] = []
    if (description.value) parts.push(description.value)
    if (model.value) parts.push(`[${model.value}]`)
    if (agentName.value) parts.push(`Agent: ${agentName.value}`)
    return {
      ...info,
      secondaryInfo: parts.join(' · ')
    }
  }
  // 展开时只显示 Agent 名称（Desc 和 Model 在详情里显示）
  if (!agentName.value) return info
  return {
    ...info,
    secondaryInfo: `Agent: ${agentName.value}`
  }
})

const description = computed(() => props.toolCall.input?.description || '')
const model = computed(() => props.toolCall.input?.model || '')
const prompt = computed(() => props.toolCall.input?.prompt || '')
const subagentMessages = computed(() => (props.toolCall as any).subagentMessages || [])

// 结果文本
const resultText = computed(() => {
  const r = props.toolCall.result
  if (!r || r.is_error) return ''
  if (typeof r.content === 'string') return r.content
  if (Array.isArray(r.content)) {
    return (r.content as any[])
      .filter((item: any) => item.type === 'text')
      .map((item: any) => item.text)
      .join('\n')
  }
  return JSON.stringify(r.content, null, 2)
})

// 是否有结果
const hasResult = computed(() => {
  const r = props.toolCall.result
  return r && !r.is_error && resultText.value
})

// 始终有参数可展示
const hasDetails = computed(() => !!description.value || !!prompt.value)
</script>

<style scoped>
.task-details {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.params-section {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.prompt-section {
  /* Desc 和 Model 移到折叠状态标题行显示，此处只有 Prompt */
}

.section-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.section-title {
  font-size: 11px;
  font-weight: 600;
  color: var(--theme-secondary-foreground);
  text-transform: uppercase;
  margin: 0;
}

.copy-btn {
  background: transparent;
  border: 1px solid var(--theme-border);
  border-radius: 4px;
  padding: 2px 6px;
  font-size: 12px;
  cursor: pointer;
  color: var(--theme-secondary-foreground);
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 28px;
  height: 22px;
}

.copy-btn:hover {
  background: var(--theme-hover-background);
  border-color: var(--theme-primary);
  color: var(--theme-primary);
}

.prompt-content {
  margin: 0;
  margin-top: 6px;
  padding: 8px;
  background: var(--theme-code-background);
  border: 1px solid var(--theme-border);
  border-radius: 4px;
  font-size: var(--theme-editor-font-size, 12px);
  color: var(--theme-foreground);
  max-height: 200px;
  overflow-y: auto;
}

.prompt-content :deep(.markdown-body) {
  font-size: 12px;
  background: transparent;
}

/* 子代理调用过程区域 */
.subagent-section {
  border-top: 1px solid var(--theme-border);
  padding-top: 8px;
  display: flex;
  flex-direction: column;
}

.section-header {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  padding: 4px 0;
  user-select: none;
}

.section-header:hover {
  opacity: 0.8;
}

.expand-icon {
  font-size: 10px;
  color: var(--theme-secondary-foreground);
  width: 12px;
}

.item-count {
  font-size: 11px;
  color: var(--theme-secondary-foreground);
  font-weight: normal;
  opacity: 0.7;
}

.subagent-container {
  margin-top: 8px;
  border: 1px solid var(--theme-border);
  border-radius: 6px;
  background: var(--theme-background);
  overflow: hidden;
}

.loading-hint {
  padding: 12px;
  text-align: center;
  font-size: 12px;
  color: var(--theme-secondary-foreground);
}

.subagent-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 8px;
  max-height: 400px;
  overflow-y: auto;
}

/* 子代理列表内部项目样式调整 - 重置嵌套组件样式 */
.subagent-list :deep(.display-item-renderer) {
  padding: 0;
  margin: 0;
  min-height: auto;
}

.subagent-list :deep(.compact-tool-card) {
  margin: 0;
  padding: 4px 8px;
  font-size: 12px;
}

.subagent-list :deep(.compact-tool-card .card-content) {
  font-size: 12px;
  min-height: 18px;
  gap: 6px;
}

.subagent-list :deep(.compact-tool-card .tool-icon) {
  font-size: 14px;
  width: 16px;
  height: 16px;
}

.subagent-list :deep(.compact-tool-card .action-type) {
  font-size: 11px;
}

.subagent-list :deep(.compact-tool-card .status-indicator) {
  width: 14px;
  height: 14px;
}

.subagent-list :deep(.compact-tool-card .status-indicator .dot) {
  width: 8px;
  height: 8px;
}

.subagent-list :deep(.compact-tool-card .status-indicator .spinner) {
  width: 10px;
  height: 10px;
  border-width: 1.5px;
}

.subagent-list :deep(.assistant-text) {
  margin: 0;
  padding: 4px 0;
  font-size: 12px;
}

.subagent-list :deep(.thinking-display) {
  margin: 0;
  padding: 4px 8px;
  font-size: 11px;
}

.subagent-list :deep(.thinking-display .thinking-header) {
  font-size: 10px;
  margin-bottom: 2px;
}

.subagent-list :deep(.thinking-display .thinking-icon) {
  font-size: 12px;
}

.subagent-list :deep(.thinking-display .thinking-content) {
  font-size: 10px;
  line-height: 1.4;
}

.subagent-list :deep(.tool-call-display) {
  margin: 0;
}

/* 结果区域 */
.result-section {
  border-top: 1px solid var(--theme-border);
  padding-top: 8px;
}

.result-content {
  margin: 0;
  margin-top: 6px;
  padding: 8px;
  background: var(--theme-code-background);
  border: 1px solid var(--theme-border);
  border-radius: 4px;
  font-size: var(--theme-editor-font-size, 12px);
  color: var(--theme-foreground);
  max-height: 300px;
  overflow-y: auto;
}

.result-content :deep(.markdown-body) {
  font-size: 12px;
  background: transparent;
}
</style>
