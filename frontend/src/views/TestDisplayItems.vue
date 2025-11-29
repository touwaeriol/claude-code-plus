<template>
  <div class="test-display-items">
    <h1>DisplayItem 组件测试</h1>

    <div class="test-section">
      <h2>Mock 数据测试</h2>
      <div class="display-items-list">
        <DisplayItemRenderer
          v-for="item in mockDisplayItems"
          :key="item.id"
          :source="item"
        />
      </div>
    </div>

    <div class="test-section">
      <h2>当前会话的 DisplayItems ({{ displayItems.length }})</h2>

      <div
        v-if="displayItems.length === 0"
        class="empty-state"
      >
        <p>没有消息。请创建会话并发送消息。</p>
      </div>

      <div
        v-else
        class="display-items-list"
      >
        <DisplayItemRenderer
          v-for="item in displayItems"
          :key="item.id"
          :source="item"
        />
      </div>
    </div>

    <div class="test-section">
      <h2>调试信息</h2>
      <div class="debug-info">
        <p><strong>当前会话 ID:</strong> {{ currentSessionId || '无' }}</p>
        <p><strong>会话数量:</strong> {{ sessionCount }}</p>
        <p><strong>消息数量:</strong> {{ messageCount }}</p>
        <p><strong>DisplayItems 数量:</strong> {{ displayItems.length }}</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive } from 'vue'
import { useSessionStore } from '@/stores/sessionStore'
import DisplayItemRenderer from '@/components/chat/DisplayItemRenderer.vue'
import type { DisplayItem, UserMessage, AssistantText, SystemMessage } from '@/types/display'
import { ToolCallStatus } from '@/types/display'
import { CLAUDE_TOOL_TYPE } from '@/constants/toolTypes'

const sessionStore = useSessionStore()

const currentSessionId = computed(() => sessionStore.currentSessionId)
const displayItems = computed(() => sessionStore.currentDisplayItems)
const sessionCount = computed(() => sessionStore.sessions.size)
const messageCount = computed(() => sessionStore.currentMessages.length)

// Mock 数据用于测试
const mockDisplayItems: DisplayItem[] = [
  // 用户消息
  {
    displayType: 'userMessage',
    id: 'mock-user-1',
    content: [{ type: 'text', text: '你好！请帮我读取 package.json 文件' }],
    timestamp: Date.now() - 10000
  } as UserMessage,

  // AI 文本回复
  {
    displayType: 'assistantText',
    id: 'mock-assistant-1',
    content: '好的，我来帮你读取 package.json 文件。',
    timestamp: Date.now() - 9000
  } as AssistantText,

  // 工具调用（Read）
  reactive({
    displayType: 'toolCall',
    toolName: 'Read',
    toolType: CLAUDE_TOOL_TYPE.READ,
    id: 'mock-tool-1',
    status: ToolCallStatus.SUCCESS,
    startTime: Date.now() - 8000,
    endTime: Date.now() - 7000,
    timestamp: Date.now() - 8000,
    input: {
      file_path: 'package.json'
    },
    result: {
      type: 'success',
      output: '{\n  "name": "claude-code-plus",\n  "version": "1.0.0",\n  "description": "Claude Code Plus"\n}',
      summary: '读取成功'
    }
  }),

  // AI 文本回复
  {
    displayType: 'assistantText',
    id: 'mock-assistant-2',
    content: '我已经成功读取了 package.json 文件。这是一个名为 "claude-code-plus" 的项目。',
    timestamp: Date.now() - 6000
  } as AssistantText,

  // 系统消息
  {
    displayType: 'systemMessage',
    id: 'mock-system-1',
    content: '会话已保存',
    level: 'info',
    timestamp: Date.now() - 5000
  } as SystemMessage
]
</script>

<style scoped>
.test-display-items {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
}

h1 {
  font-size: 24px;
  margin-bottom: 20px;
  color: #333;
}

h2 {
  font-size: 18px;
  margin-bottom: 12px;
  color: #555;
}

.test-section {
  margin-bottom: 30px;
  padding: 16px;
  background: #f9f9f9;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
}

.empty-state {
  padding: 40px;
  text-align: center;
  color: #999;
}

.display-items-list {
  background: #fff;
  padding: 16px;
  border-radius: 6px;
}

.debug-info {
  font-family: monospace;
  font-size: 13px;
  line-height: 1.8;
}

.debug-info p {
  margin: 4px 0;
}

.debug-info strong {
  color: #333;
}
</style>
