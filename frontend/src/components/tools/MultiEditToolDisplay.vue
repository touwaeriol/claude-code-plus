<template>
  <div class="tool-display multi-edit-tool">
    <div class="tool-header">
      <span class="tool-icon">📝</span>
      <span class="tool-name">MultiEdit</span>
      <span class="tool-file">{{ fileName }}</span>
      <span class="edit-count-badge">{{ edits.length }} 处修改</span>
    </div>
    <div
      v-if="expanded"
      class="tool-content"
    >
      <!-- 修改列表 -->
      <div class="edits-list">
        <div
          v-for="(edit, index) in edits"
          :key="index"
          class="edit-item"
        >
          <div
            class="edit-header"
            @click="toggleEdit(index)"
          >
            <span class="edit-number">修改 #{{ index + 1 }}</span>
            <span
              v-if="edit.replace_all"
              class="badge replace-all"
            >全部替换</span>
            <span class="expand-icon">{{ expandedEdits.has(index) ? '▼' : '▶' }}</span>
          </div>

          <div
            v-if="expandedEdits.has(index)"
            class="edit-content"
          >
            <div class="edit-preview">
              <div class="diff-section">
                <div class="diff-header old">
                  <span></span>
                  <button
                    class="copy-btn"
                    title="复制"
                    @click="copyText(edit.old_string)"
                  >
                    <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                      <rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect>
                      <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path>
                    </svg>
                  </button>
                </div>
                <pre class="diff-content old">{{ edit.old_string }}</pre>
              </div>
              <div class="diff-arrow">
                →
              </div>
              <div class="diff-section">
                <div class="diff-header new">
                  <span></span>
                  <button
                    class="copy-btn"
                    title="复制"
                    @click="copyText(edit.new_string)"
                  >
                    <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                      <rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect>
                      <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path>
                    </svg>
                  </button>
                </div>
                <pre class="diff-content new">{{ edit.new_string }}</pre>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 操作按钮 -->
      <div class="actions">
        <button
          class="action-btn primary"
          @click="showAllDiffs"
        >
          查看完整 Diff
        </button>
        <button
          class="action-btn"
          @click="expandAll"
        >
          {{ allExpanded ? '全部收起' : '全部展开' }}
        </button>
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
import { ideService } from '@/services/ideaBridge'
import { useEnvironment } from '@/composables/useEnvironment'
import type { ToolUseBlock, ToolResultBlock } from '@/types/message'

interface EditOperation {
  old_string: string
  new_string: string
  replace_all?: boolean
}

interface Props {
  toolUse: ToolUseBlock
  result?: ToolResultBlock
}

const props = defineProps<Props>()
const expanded = ref(false)
const expandedEdits = ref(new Set<number>())

const filePath = computed(() => props.toolUse.input.file_path || '')
const fileName = computed(() => {
  const path = filePath.value
  return path.split(/[\\/]/).pop() || path
})

const edits = computed<EditOperation[]>(() => {
  return props.toolUse.input.edits || []
})

const allExpanded = computed(() => {
  return expandedEdits.value.size === edits.value.length
})

function toggleEdit(index: number) {
  if (expandedEdits.value.has(index)) {
    expandedEdits.value.delete(index)
  } else {
    expandedEdits.value.add(index)
  }
  // 触发响应式更新
  expandedEdits.value = new Set(expandedEdits.value)
}

function expandAll() {
  if (allExpanded.value) {
    expandedEdits.value.clear()
  } else {
    expandedEdits.value = new Set(edits.value.map((_, i) => i))
  }
}

async function openFile() {
  await ideService.openFile(filePath.value)
}

async function showAllDiffs() {
  // 使用增强功能：从文件重建完整 Diff，显示所有修改
  await ideService.showDiff({
    filePath: filePath.value,
    oldContent: edits.value[0]?.old_string || '',
    newContent: edits.value[0]?.new_string || '',
    rebuildFromFile: true,
    title: `文件变更: ${filePath.value} (${edits.value.length} 处修改)`,
    edits: edits.value.map(edit => ({
      oldString: edit.old_string,
      newString: edit.new_string,
      replaceAll: edit.replace_all || false
    }))
  })
}

async function copyText(text: string) {
  await navigator.clipboard.writeText(text)
}
</script>

<style scoped>
.multi-edit-tool {
  border-color: #f9826c;
}

.multi-edit-tool .tool-name {
  color: #d73a49;
}

.edit-count-badge {
  display: inline-block;
  padding: 2px 8px;
  background: #d73a49;
  color: white;
  font-size: 11px;
  font-weight: 600;
  border-radius: 10px;
  margin-left: 4px;
}

.edits-list {
  margin: 12px 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.edit-item {
  border: 1px solid var(--ide-border);
  border-radius: 6px;
  overflow: hidden;
  background: var(--ide-panel-background);
}

.edit-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: var(--ide-panel-background);
  border-bottom: 1px solid var(--ide-border);
  cursor: pointer;
  user-select: none;
}

.edit-header:hover {
  background: var(--ide-code-background);
}

.edit-number {
  font-weight: 600;
  font-size: 13px;
  color: var(--ide-foreground);
  flex: 1;
}

.badge.replace-all {
  display: inline-block;
  padding: 2px 6px;
  background: #0366d6;
  color: white;
  font-size: 11px;
  font-weight: 600;
  border-radius: 10px;
}

.expand-icon {
  color: var(--ide-foreground);
  opacity: 0.6;
  font-size: 10px;
}

.edit-content {
  padding: 12px;
}

.edit-preview {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  gap: 12px;
}

.diff-section {
  position: relative;
  border: 1px solid var(--ide-border);
  border-radius: 4px;
  overflow: hidden;
}

.diff-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 12px;
  font-size: 12px;
  font-weight: 600;
  border-bottom: 1px solid var(--ide-border);
}

.diff-header.old {
  background: #ffeef0;
  color: #d73a49;
}

.diff-header.new {
  background: #e6ffed;
  color: #22863a;
}

/* 暗色主题下的颜色调整 */
.theme-dark .diff-header.old {
  background: #3d1f1f;
  color: #f85149;
}

.theme-dark .diff-header.new {
  background: #1f3d1f;
  color: #56d364;
}

.diff-content {
  margin: 0;
  padding: 12px;
  font-size: 12px;
  font-family: 'Consolas', 'Monaco', monospace;
  max-height: 200px;
  overflow: auto;
  background: var(--ide-background);
  color: var(--ide-foreground);
  white-space: pre-wrap;
  word-break: break-all;
}

.diff-content.old {
  background: #ffeef0;
  color: #24292e;
}

.diff-content.new {
  background: #e6ffed;
  color: #24292e;
}

/* 暗色主题下的内容颜色 */
.theme-dark .diff-content.old {
  background: #3d1f1f;
  color: #e1e4e8;
}

.theme-dark .diff-content.new {
  background: #1f3d1f;
  color: #e1e4e8;
}

.diff-arrow {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  color: var(--ide-foreground);
  opacity: 0.6;
}

.copy-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 4px;
  border: none;
  border-radius: 3px;
  background: transparent;
  color: var(--ide-foreground, #24292e);
  cursor: pointer;
  opacity: 0.6;
}

.copy-btn:hover {
  opacity: 1;
  background: var(--ide-panel-background, #f6f8fa);
}

.actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

.action-btn {
  padding: 6px 12px;
  font-size: 13px;
  border: 1px solid var(--ide-border);
  border-radius: 4px;
  background: var(--ide-panel-background);
  color: var(--ide-foreground);
  cursor: pointer;
  transition: all 0.2s;
}

.action-btn:hover {
  background: var(--ide-code-background);
  border-color: var(--ide-accent);
}

.action-btn.primary {
  background: var(--ide-button-background);
  color: var(--ide-button-foreground);
  border-color: var(--ide-button-background);
}

.action-btn.primary:hover {
  background: var(--ide-button-hover-background);
  border-color: var(--ide-button-hover-background);
}


</style>
