<template>
  <div v-if="hasChanges" class="file-rollback-bar">
    <!-- 头部：可点击展开/收起 -->
    <div class="rollback-header">
      <div class="header-left" @click="toggleExpanded">
        <span class="rollback-icon">↩</span>
        <span class="rollback-title">{{ t('tools.modifiedFiles') }}</span>
        <span class="file-count">({{ t('tools.fileEditStats', { files: changedFileCount, edits: totalEditCount }) }})</span>
        <span class="expand-icon" :class="{ expanded }">▾</span>
      </div>
      <div v-if="isIdeMode" class="header-actions">
        <button
          class="accept-all-btn"
          :title="t('tools.acceptAll')"
          @click="handleAcceptAll"
        >
          ✓ {{ t('tools.acceptAll') }}
        </button>
        <button
          class="rollback-all-header-btn"
          :class="{ loading: isRollingBackAll }"
          :disabled="isRollingBackAll"
          :title="t('tools.rollbackAll')"
          @click="handleRollbackAll"
        >
          <span v-if="isRollingBackAll" class="spinner" />
          <span v-else>↩ {{ t('tools.rollbackAll') }}</span>
        </button>
      </div>
    </div>

    <!-- 展开内容 -->
    <div v-if="expanded" class="file-list">
      <div
        v-for="file in fileChanges"
        :key="file.filePath"
        class="file-group"
      >
        <!-- 文件头部 -->
        <div class="file-header">
          <div class="file-header-left" @click="toggleFileExpanded(file.filePath)">
            <span 
              class="file-collapse-btn"
              :class="{ collapsed: !isFileExpanded(file.filePath) }"
            >
              ▾
            </span>
            <span class="file-icon">📄</span>
            <span 
              class="file-name" 
              :title="file.filePath"
              @click.stop="openFile(file.filePath)"
            >
              {{ file.fileName }}
            </span>
            <span class="file-stats">
              {{ getFileStatsData(file).editCount }} edit{{ getFileStatsData(file).editCount > 1 ? 's' : '' }}<template v-if="getFileStatsData(file).totalAdded > 0 || getFileStatsData(file).totalRemoved > 0">,</template>
              <span v-if="getFileStatsData(file).totalAdded > 0" class="stats-added">+{{ getFileStatsData(file).totalAdded }}</span>
              <span v-if="getFileStatsData(file).totalRemoved > 0" class="stats-removed">-{{ getFileStatsData(file).totalRemoved }}</span>
            </span>
          </div>
          <div v-if="isIdeMode" class="file-actions">
            <button
              class="accept-file-btn"
              :title="t('tools.acceptFile')"
              @click="handleAcceptFile(file.filePath)"
            >
              ✓ {{ t('tools.accept') }}
            </button>
            <button
              class="rollback-all-btn"
              :class="{ loading: isRollingBack(file.filePath) }"
              :disabled="isRollingBack(file.filePath) || isRollingBackAll"
              :title="t('tools.rollbackAll')"
              @click="handleRollbackFile(file.filePath)"
            >
              <span v-if="isRollingBack(file.filePath)" class="spinner" />
              <span v-else>↩ {{ t('tools.rollbackAll') }}</span>
            </button>
          </div>
        </div>

        <!-- 修改列表 -->
        <div v-if="isFileExpanded(file.filePath)" class="modification-list">
          <div
            v-for="mod in getActiveModifications(file)"
            :key="mod.toolUseId"
            class="modification-item"
          >
            <span class="mod-tool">{{ mod.toolName }}</span>
            <span 
              class="mod-summary" 
              :title="mod.summary"
              @click="handleViewDiff(mod)"
            >
              {{ mod.summary }}
            </span>
            <span v-if="mod.linesAdded" class="line-badge badge-add">+{{ mod.linesAdded }}</span>
            <span v-if="mod.linesRemoved" class="line-badge badge-remove">-{{ mod.linesRemoved }}</span>
            <div class="mod-actions">
              <button
                class="mod-action-btn view-btn"
                :title="t('tools.viewDiff')"
                @click="handleViewDiff(mod)"
              >
                👁
              </button>
              <button
                v-if="isIdeMode"
                class="mod-action-btn accept-btn"
                :title="t('tools.acceptModification')"
                @click="handleAcceptModification(file.filePath, mod.historyTs)"
              >
                ✓
              </button>
              <button
                v-if="isIdeMode"
                class="mod-action-btn rollback-btn"
                :class="{ loading: isToolRollingBack(mod.toolUseId) }"
                :disabled="isRollingBackAll || isToolRollingBack(mod.toolUseId)"
                :title="t('tools.rollbackModification')"
                @click="handleRollbackModification(file.filePath, mod.historyTs)"
              >
                <span v-if="isToolRollingBack(mod.toolUseId)" class="spinner-small" />
                <span v-else>↩</span>
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 收起状态：简洁显示 -->
    <div v-else class="file-tags">
      <div
        v-for="file in fileChanges"
        :key="file.filePath"
        class="file-tag"
      >
        <span class="file-name" :title="file.filePath">{{ file.fileName }}</span>
        <span class="file-tag-stats">
          {{ getFileStatsData(file).editCount }} edit{{ getFileStatsData(file).editCount > 1 ? 's' : '' }}<template v-if="getFileStatsData(file).totalAdded > 0">,<span class="stats-added">+{{ getFileStatsData(file).totalAdded }}</span></template><template v-if="getFileStatsData(file).totalRemoved > 0">,<span class="stats-removed">-{{ getFileStatsData(file).totalRemoved }}</span></template>
        </span>
        <button
          v-if="isIdeMode"
          class="rollback-tag-btn"
          :class="{ loading: isRollingBack(file.filePath) }"
          :disabled="isRollingBack(file.filePath) || isRollingBackAll"
          @click.stop="handleRollbackFile(file.filePath)"
        >
          <span v-if="isRollingBack(file.filePath)" class="spinner" />
          <span v-else>↩</span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from '@/composables/useI18n'
import { ideaBridge, getFileContent } from '@/services/ideaBridge'
import { ideaRSocket } from '@/services/ideaRSocket'
import { useSessionStore } from '@/stores/sessionStore'
import { useToastStore } from '@/stores/toastStore'
import type { FileChange, FileModification } from '@/composables/useFileChanges'

const { t } = useI18n()

// 直接从 sessionStore 获取 fileChanges 实例（响应式）
const sessionStore = useSessionStore()
const toastStore = useToastStore()
const fileChangesInstance = computed(() => sessionStore.currentFileChanges)

// 检测是否在 IDE 模式
const isIdeMode = computed(() => ideaBridge.isInIde())

// 展开/收起状态（整体）
const expanded = ref(false)

// 文件级别展开状态
const expandedFiles = ref<Set<string>>(new Set())

// 从 composable 获取数据
const fileChanges = computed(() => fileChangesInstance.value?.fileChanges.value ?? [])
const hasChanges = computed(() => fileChangesInstance.value?.hasChanges.value ?? false)
const changedFileCount = computed(() => fileChangesInstance.value?.changedFileCount.value ?? 0)
const totalEditCount = computed(() => fileChangesInstance.value?.totalEditCount.value ?? 0)

// 检查文件是否正在回滚
function isRollingBack(filePath: string): boolean {
  return fileChangesInstance.value?.isRollingBack(filePath) ?? false
}

// 检查单个工具调用是否正在回滚
function isToolRollingBack(toolUseId: string): boolean {
  return fileChangesInstance.value?.isToolRollingBack(toolUseId) ?? false
}

// 获取文件的活跃修改（未回滚、未接受的）
function getActiveModifications(file: FileChange) {
  return file.modifications.filter(m => !m.rolledBack && !m.accepted)
}

// 获取文件统计信息（返回对象）
function getFileStatsData(file: FileChange) {
  const mods = getActiveModifications(file)
  const editCount = mods.length
  const totalAdded = mods.reduce((sum, m) => sum + (m.linesAdded || 0), 0)
  const totalRemoved = mods.reduce((sum, m) => sum + (m.linesRemoved || 0), 0)
  return { editCount, totalAdded, totalRemoved }
}

// 切换整体展开/收起
function toggleExpanded() {
  expanded.value = !expanded.value
  // 展开时不自动展开所有文件，保持折叠状态
}

// 检查文件是否展开
function isFileExpanded(filePath: string): boolean {
  return expandedFiles.value.has(filePath)
}

// 切换文件展开/收起
function toggleFileExpanded(filePath: string) {
  if (expandedFiles.value.has(filePath)) {
    expandedFiles.value.delete(filePath)
  } else {
    expandedFiles.value.add(filePath)
  }
}

// 打开文件
async function openFile(filePath: string) {
  try {
    await ideaRSocket.openFile({ filePath })
  } catch (error) {
    console.error('Failed to open file:', error)
  }
}

// 查看 Diff
async function handleViewDiff(mod: FileModification) {
  // IDE 模式：在 IDEA 中打开 Diff
  if (isIdeMode.value) {
    try {
      // 获取历史内容
      const originalContent = await ideaRSocket.getFileHistoryContent(mod.filePath, mod.historyTs)
      if (originalContent !== null) {
        // 获取当前内容
        const response = await getFileContent(mod.filePath)
        const currentContent = (response?.data as { content?: string })?.content ?? ''

        await ideaRSocket.showDiff({
          filePath: mod.filePath,
          oldContent: originalContent,
          newContent: currentContent,
          title: `${mod.toolName}: ${mod.summary}`
        })
      }
    } catch (error) {
      console.error('Failed to show diff:', error)
    }
  } else {
    // 浏览器模式：滚动到对应工具条并展开
    scrollToToolCardAndExpand(mod.toolUseId)
  }
}

// 滚动到工具条并展开
function scrollToToolCardAndExpand(toolUseId: string) {
  const el = document.querySelector(`[data-tool-use-id="${toolUseId}"]`)
  if (el) {
    // 滚动到元素位置
    el.scrollIntoView({ behavior: 'smooth', block: 'center' })
    // 延迟后触发点击展开（等待滚动完成）
    setTimeout(() => {
      el.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    }, 300)
  }
}

// 处理接受所有改动
function handleAcceptAll() {
  if (!fileChangesInstance.value) return
  fileChangesInstance.value.acceptAll()
}

// 是否正在批量回滚
const isRollingBackAll = computed(() => fileChangesInstance.value?.isRollingBackAll.value ?? false)

// 处理回滚所有改动（使用流式批量回滚 API）
async function handleRollbackAll() {
  if (!fileChangesInstance.value) return
  
  // 如果正在回滚，忽略
  if (isRollingBackAll.value) return
  
  // 调用批量回滚
  const result = await fileChangesInstance.value.rollbackAll()
  
  if (!result.success) {
    if (result.failedCount > 0) {
      toastStore.error(t('tools.rollbackPartialFailed', { count: result.failedCount }))
    } else {
      toastStore.error(t('tools.rollbackFailed'))
    }
  }
}

// 处理接受单个文件的所有改动
function handleAcceptFile(filePath: string) {
  if (!fileChangesInstance.value) return
  fileChangesInstance.value.acceptFile(filePath)
}

// 处理接受单个修改
function handleAcceptModification(filePath: string, historyTs: number) {
  if (!fileChangesInstance.value) return
  fileChangesInstance.value.acceptModification(filePath, historyTs)
}

// 处理回滚整个文件
async function handleRollbackFile(filePath: string) {
  if (!fileChangesInstance.value) return
  
  const file = fileChanges.value.find(f => f.filePath === filePath)
  if (!file) return
  
  const result = await fileChangesInstance.value.rollbackFile(filePath)
  if (!result.success) {
    console.error('Rollback failed:', result.error)
    toastStore.error(t('tools.rollbackFailed') + ': ' + result.error)
  }
}

// 处理回滚单个修改
async function handleRollbackModification(filePath: string, historyTs: number) {
  if (!fileChangesInstance.value) return
  
  const result = await fileChangesInstance.value.rollbackModification(filePath, historyTs)
  if (!result.success) {
    console.error('Rollback failed:', result.error)
    toastStore.error(t('tools.rollbackFailed') + ': ' + result.error)
  }
}
</script>

<style scoped>
.file-rollback-bar {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 8px 10px;
  background: color-mix(in srgb, var(--theme-accent) 6%, var(--theme-panel-background));
  border: 1px solid color-mix(in srgb, var(--theme-accent) 20%, var(--theme-border));
  border-radius: 6px;
  margin-bottom: 6px;
}

.rollback-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--theme-accent);
  cursor: pointer;
  user-select: none;
}

.header-left:hover {
  opacity: 0.85;
}

.rollback-icon {
  font-size: 14px;
}

.rollback-title {
  font-weight: 600;
}

.file-count {
  color: var(--theme-secondary-foreground);
  font-weight: normal;
}

.expand-icon {
  transition: transform 0.2s ease;
  font-size: 10px;
}

.expand-icon.expanded {
  transform: rotate(180deg);
}

.header-actions {
  display: flex;
  gap: 6px;
}

.accept-all-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border: 1px solid color-mix(in srgb, var(--theme-success) 50%, transparent);
  border-radius: 4px;
  background: color-mix(in srgb, var(--theme-success) 10%, transparent);
  color: var(--theme-success);
  font-size: 11px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s ease;
}

.accept-all-btn:hover {
  background: color-mix(in srgb, var(--theme-success) 20%, transparent);
}

.rollback-all-header-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border: 1px solid color-mix(in srgb, var(--theme-warning) 50%, transparent);
  border-radius: 4px;
  background: color-mix(in srgb, var(--theme-warning) 10%, transparent);
  color: var(--theme-foreground);
  font-size: 11px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s ease;
}

.rollback-all-header-btn:hover {
  background: color-mix(in srgb, var(--theme-warning) 20%, transparent);
}

/* 收起状态：标签列表 */
.file-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.file-tag {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 6px 4px 10px;
  background: var(--theme-panel-background);
  border: 1px solid var(--theme-border);
  border-radius: 4px;
  font-size: 12px;
}

.file-tag .file-name {
  max-width: 150px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--theme-foreground);
}

.file-tag-stats {
  font-size: 11px;
  color: var(--theme-secondary-foreground);
  white-space: nowrap;
}

.file-tag-stats .stats-added {
  color: var(--theme-success, #22c55e);
}

.file-tag-stats .stats-removed {
  color: var(--theme-error, #ef4444);
}

.rollback-tag-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  padding: 0;
  border: none;
  border-radius: 3px;
  background: transparent;
  color: var(--theme-secondary-foreground);
  cursor: pointer;
  font-size: 11px;
  transition: all 0.15s ease;
}

.rollback-tag-btn:hover:not(:disabled) {
  background: color-mix(in srgb, var(--theme-warning) 20%, transparent);
}

/* 展开状态：文件组 */
.file-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.file-group {
  background: var(--theme-panel-background);
  border: 1px solid var(--theme-border);
  border-radius: 4px;
  overflow: hidden;
}

.file-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 4px;
  padding: 4px 8px;
  background: color-mix(in srgb, var(--theme-foreground) 3%, transparent);
}

.file-header-left {
  display: flex;
  align-items: center;
  gap: 4px;
  flex: 1;
  min-width: 0;
  cursor: pointer;
  user-select: none;
}

.file-header-left:hover {
  opacity: 0.85;
}

.file-collapse-btn {
  font-size: 10px;
  color: var(--theme-secondary-foreground);
  cursor: pointer;
  transition: transform 0.2s ease;
  user-select: none;
}

.file-collapse-btn.collapsed {
  transform: rotate(-90deg);
}

.file-icon {
  font-size: 11px;
}

.file-header .file-name {
  font-size: 12px;
  font-weight: 500;
  color: var(--theme-link);
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-header .file-name:hover {
  text-decoration: underline;
}

.file-stats {
  flex: 1;
  font-size: 11px;
  color: var(--theme-secondary-foreground);
}

.stats-added {
  color: var(--theme-success);
  font-weight: 500;
}

.stats-removed {
  color: var(--theme-error);
  font-weight: 500;
}

.file-actions {
  display: flex;
  gap: 6px;
}

.accept-file-btn {
  display: flex;
  align-items: center;
  gap: 3px;
  padding: 2px 6px;
  border: 1px solid color-mix(in srgb, var(--theme-success) 40%, transparent);
  border-radius: 3px;
  background: transparent;
  color: var(--theme-success);
  font-size: 10px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.accept-file-btn:hover {
  background: color-mix(in srgb, var(--theme-success) 15%, transparent);
}

.rollback-all-btn {
  display: flex;
  align-items: center;
  gap: 3px;
  padding: 2px 6px;
  border: 1px solid color-mix(in srgb, var(--theme-warning) 40%, transparent);
  border-radius: 3px;
  background: transparent;
  color: var(--theme-foreground);
  font-size: 10px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.rollback-all-btn:hover:not(:disabled) {
  background: color-mix(in srgb, var(--theme-warning) 15%, transparent);
}

.rollback-all-btn:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

/* 修改列表 */
.modification-list {
  display: flex;
  flex-direction: column;
}

.modification-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 8px 4px 24px;
  font-size: 11px;
  border-bottom: 1px solid color-mix(in srgb, var(--theme-border) 50%, transparent);
}

.modification-item:last-child {
  border-bottom: none;
}

.mod-tool {
  padding: 2px 6px;
  background: color-mix(in srgb, var(--theme-accent) 15%, transparent);
  border-radius: 3px;
  color: var(--theme-accent);
  font-size: 10px;
  font-weight: 600;
  text-transform: uppercase;
}

.mod-summary {
  flex: 1;
  color: var(--theme-secondary-foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
}

.mod-summary:hover {
  color: var(--theme-foreground);
}

.line-badge {
  padding: 1px 4px;
  border-radius: 3px;
  font-size: 10px;
  font-weight: 600;
}

.badge-add {
  background: color-mix(in srgb, var(--theme-success) 15%, transparent);
  color: var(--theme-success);
}

.badge-remove {
  background: color-mix(in srgb, var(--theme-error) 15%, transparent);
  color: var(--theme-error);
}

.mod-actions {
  display: flex;
  gap: 4px;
}

.mod-action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  padding: 0;
  border: none;
  border-radius: 3px;
  background: transparent;
  cursor: pointer;
  font-size: 12px;
  transition: all 0.15s ease;
}

.mod-action-btn.accept-btn {
  color: var(--theme-secondary-foreground);
}

.mod-action-btn.accept-btn:hover {
  background: color-mix(in srgb, var(--theme-success) 20%, transparent);
  color: var(--theme-success);
}

.mod-action-btn.view-btn {
  color: var(--theme-secondary-foreground);
}

.mod-action-btn.view-btn:hover {
  background: color-mix(in srgb, var(--theme-accent) 20%, transparent);
  color: var(--theme-accent);
}

.mod-action-btn.rollback-btn {
  color: var(--theme-secondary-foreground);
}

.mod-action-btn.rollback-btn:hover {
  background: color-mix(in srgb, var(--theme-warning) 20%, transparent);
}

/* 加载状态 */
.spinner {
  width: 10px;
  height: 10px;
  border: 1.5px solid transparent;
  border-top-color: var(--theme-warning);
  border-right-color: var(--theme-warning);
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

.spinner-small {
  width: 8px;
  height: 8px;
  border: 1px solid transparent;
  border-top-color: var(--theme-warning);
  border-right-color: var(--theme-warning);
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
