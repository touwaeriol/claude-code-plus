<template>
  <div class="tool-display websearch-tool">
    <div class="tool-header">
      <span class="tool-icon">🔍</span>
      <span class="tool-name">WebSearch</span>
      <code class="tool-query">{{ queryPreview }}</code>
    </div>
    <div v-if="expanded" class="tool-content">
      <div class="search-info">
        <div class="info-row">
          <span class="label">{{ t('tools.label.query') }}:</span>
          <span class="value">{{ query }}</span>
        </div>
        <div v-if="allowedDomains.length > 0" class="info-row">
          <span class="label">{{ t('tools.label.allowedDomains') }}:</span>
          <div class="domain-list">
            <span v-for="(domain, index) in allowedDomains" :key="index" class="domain-tag allowed">
              {{ domain }}
            </span>
          </div>
        </div>
        <div v-if="blockedDomains.length > 0" class="info-row">
          <span class="label">{{ t('tools.label.blockedDomains') }}:</span>
          <div class="domain-list">
            <span v-for="(domain, index) in blockedDomains" :key="index" class="domain-tag blocked">
              {{ domain }}
            </span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from '@/composables/useI18n'
import type { ClaudeWebSearchToolCall } from '@/types/display'

const { t } = useI18n()

interface Props {
  toolCall: ClaudeWebSearchToolCall
}

const props = defineProps<Props>()
// 默认折叠，点击后展开查看搜索结果
const expanded = ref(false)

const query = computed(() => props.toolCall.input.query || '')
const queryPreview = computed(() => query.value.length > 40 ? `${query.value.slice(0, 40)}...` : query.value)
const allowedDomains = computed(() => props.toolCall.input.allowed_domains || [])
const blockedDomains = computed(() => props.toolCall.input.blocked_domains || [])
</script>

<style scoped>
.tool-display {
  border: 1px solid var(--ide-border, #e1e4e8);
  border-radius: 6px;
  background: var(--ide-panel-background, #f6f8fa);
  margin: 8px 0;
  padding: 8px 12px;
}

.tool-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.tool-icon {
  font-size: 16px;
}

.tool-name {
  font-weight: 600;
}

.tool-query {
  background: #eef2ff;
  color: #4338ca;
  padding: 2px 6px;
  border-radius: 4px;
}

.tool-content {
  margin-top: 8px;
}

.search-info {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.info-row {
  display: flex;
  gap: 8px;
  font-size: 12px;
}

.label {
  color: #586069;
  min-width: 72px;
}

.value {
  color: #24292e;
}

.domain-list {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.domain-tag {
  padding: 2px 6px;
  border-radius: 10px;
  font-size: 11px;
}

.domain-tag.allowed {
  background: #e6ffed;
  color: #22863a;
}

.domain-tag.blocked {
  background: #ffeef0;
  color: #d73a49;
}
</style>
