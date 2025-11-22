<template>
  <div class="tool-display list-mcp-resources-tool">
    <div class="tool-header">
      <span class="tool-icon">📚</span>
      <span class="tool-name">ListMcpResources</span>
      <span
        v-if="server"
        class="server-badge"
      >{{ server }}</span>
      <span
        v-if="resourceCount !== null"
        class="count-badge"
      >{{ resourceCount }} 项</span>
    </div>
    <div
      v-if="expanded"
      class="tool-content"
    >
      <div
        v-if="server"
        class="tool-meta"
      >
        <div class="info-row">
          <span class="label">服务器:</span>
          <span class="value">{{ server }}</span>
        </div>
      </div>

      <div
        v-if="result"
        class="resources-section"
      >
        <div class="section-header">
          <span>资源列表</span>
          <span
            v-if="resourceCount !== null"
            class="count-text"
          >{{ resourceCount }} 个资源</span>
        </div>

        <div
          v-if="resources && resources.length > 0"
          class="resources-list"
        >
          <div
            v-for="(resource, index) in resources"
            :key="index"
            class="resource-card"
          >
            <div class="resource-header">
              <span class="resource-icon">📄</span>
              <span class="resource-name">{{ resource.name }}</span>
              <span
                v-if="resource.mimeType"
                class="mime-badge"
              >{{ resource.mimeType }}</span>
            </div>
            <div class="resource-uri">
              <span class="uri-label">URI:</span>
              <code class="uri-value">{{ resource.uri }}</code>
            </div>
            <div
              v-if="resource.description"
              class="resource-description"
            >
              {{ resource.description }}
            </div>
            <div class="resource-server">
              <span class="server-label">服务器:</span>
              <span class="server-value">{{ resource.server }}</span>
            </div>
          </div>
        </div>
        <div
          v-else
          class="no-resources"
        >
          {{ success ? '未找到资源' : '加载失败' }}
        </div>
      </div>
    </div>
    <button
      class="expand-btn"
      @click="expanded = !expanded"
    >
      {{ expanded ? '收起' : '展开' }}
    </button>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { ToolUseBlock, ToolResultBlock } from '@/types/message'

interface McpResource {
  uri: string
  name: string
  description?: string
  mimeType?: string
  server: string
}

interface Props {
  toolUse: ToolUseBlock
  result?: ToolResultBlock
}

const props = defineProps<Props>()
const expanded = ref(false)

const server = computed(() => props.toolUse.input.server || '')

const resultContent = computed(() => {
  if (!props.result?.content) return null
  if (typeof props.result.content === 'string') {
    try {
      return JSON.parse(props.result.content)
    } catch {
      return null
    }
  }
  return props.result.content
})

const success = computed(() => {
  if (!resultContent.value) return false
  return resultContent.value.success === true
})

const resources = computed((): McpResource[] | null => {
  if (!resultContent.value || !success.value) return null
  return resultContent.value.resources || []
})

const resourceCount = computed(() => {
  if (!resources.value) return null
  return resources.value.length
})
</script>

<style scoped>
.list-mcp-resources-tool {
  border-color: #6f42c1;
}

.list-mcp-resources-tool .tool-name {
  color: #6f42c1;
}

.server-badge {
  font-size: 11px;
  background: rgba(111, 66, 193, 0.1);
  color: #6f42c1;
  padding: 2px 8px;
  border-radius: 10px;
  font-weight: 600;
}

.count-badge {
  margin-left: auto;
  font-size: 11px;
  background: #f6f8fa;
  color: #586069;
  padding: 2px 8px;
  border-radius: 10px;
  font-weight: 600;
}

.tool-meta {
  margin-bottom: 2px;
}

.info-row {
  display: flex;
  gap: 8px;
  margin-bottom: 4px;
  font-size: 13px;
}

.info-row .label {
  font-weight: 600;
  color: #586069;
  min-width: 80px;
}

.info-row .value {
  font-family: monospace;
  color: #24292e;
}

.resources-section {
  margin-top: 12px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  font-weight: 600;
  color: #586069;
  margin-bottom: 2px;
}

.count-text {
  color: #0366d6;
}

.resources-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.resource-card {
  padding: 12px;
  background: #f6f8fa;
  border: 1px solid #e1e4e8;
  border-radius: 6px;
}

.resource-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 2px;
}

.resource-icon {
  font-size: 14px;
}

.resource-name {
  font-weight: 600;
  font-size: 13px;
  color: #24292e;
}

.mime-badge {
  margin-left: auto;
  font-size: 10px;
  background: #ffffff;
  border: 1px solid #e1e4e8;
  padding: 2px 6px;
  border-radius: 3px;
  color: #586069;
  font-family: monospace;
}

.resource-uri {
  display: flex;
  gap: 6px;
  margin-bottom: 6px;
  font-size: 12px;
}

.uri-label {
  font-weight: 600;
  color: #586069;
}

.uri-value {
  font-family: 'Consolas', 'Monaco', monospace;
  background: #ffffff;
  padding: 2px 6px;
  border-radius: 3px;
  color: #0366d6;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.resource-description {
  font-size: 12px;
  color: #586069;
  margin-bottom: 6px;
  line-height: 1.5;
}

.resource-server {
  display: flex;
  gap: 6px;
  font-size: 11px;
}

.server-label {
  font-weight: 600;
  color: #586069;
}

.server-value {
  color: #6f42c1;
  font-weight: 600;
}

.no-resources {
  padding: 24px;
  text-align: center;
  color: #586069;
  font-size: 13px;
  font-style: italic;
  background: #f6f8fa;
  border: 1px solid #e1e4e8;
  border-radius: 4px;
}
</style>
