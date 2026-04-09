<template>
  <div class="session-list-with-groups">
    <!-- 未分组的会话 -->
    <div v-if="ungroupedSessions.length > 0" class="session-group">
      <div class="group-header">
        <span class="group-icon">📋</span>
        <span class="group-name">未分组</span>
        <span class="session-count">({{ ungroupedSessions.length }})</span>
      </div>
      <div class="sessions">
        <div
          v-for="session in ungroupedSessions"
          :key="session.id"
          class="session-item"
          :class="{ active: session.id === activeSessionId }"
          @click="$emit('select-session', session.id)"
        >
          <div class="session-info">
            <div class="session-name">{{ session.name }}</div>
            <div class="session-time">{{ formatTime(session.createdAt) }}</div>
          </div>
          <div class="session-tags">
            <span
              v-for="tag in getSessionTags(session.id)"
              :key="tag.id"
              class="tag"
              :style="{ backgroundColor: tag.color + '30', color: tag.color }"
            >
              {{ tag.name }}
            </span>
          </div>
        </div>
      </div>
    </div>

    <!-- 分组的会话 -->
    <div v-for="group in groups" :key="group.id" class="session-group">
      <div
        class="group-header"
        :style="{ borderLeftColor: group.color }"
        @click="toggleGroup(group.id)"
      >
        <span class="group-icon">{{ group.icon || '📁' }}</span>
        <span class="group-name">{{ group.name }}</span>
        <span class="session-count">({{ getGroupSessions(group.id).length }})</span>
        <span class="collapse-icon">{{ group.isCollapsed ? '▶' : '▼' }}</span>
      </div>

      <div v-if="!group.isCollapsed" class="sessions">
        <div
          v-for="session in getGroupSessions(group.id)"
          :key="session.id"
          class="session-item"
          :class="{ active: session.id === activeSessionId }"
          @click="$emit('select-session', session.id)"
          @contextmenu.prevent="showSessionContextMenu(session.id, $event)"
        >
          <div class="session-info">
            <div class="session-name">{{ session.name }}</div>
            <div class="session-time">{{ formatTime(session.createdAt) }}</div>
          </div>
          <div class="session-tags">
            <span
              v-for="tag in getSessionTags(session.id)"
              :key="tag.id"
              class="tag"
              :style="{ backgroundColor: tag.color + '30', color: tag.color }"
            >
              {{ tag.name }}
            </span>
          </div>
        </div>
      </div>
    </div>

    <!-- 右键菜单 -->
    <div
      v-if="contextMenu.show"
      class="context-menu"
      :style="{ top: contextMenu.y + 'px', left: contextMenu.x + 'px' }"
      @click.stop
    >
      <div class="menu-item" @click="showMoveToGroupDialog">
        <span class="menu-icon">📁</span>
        移动到分组
      </div>
      <div class="menu-item" @click="showAddTagDialog">
        <span class="menu-icon">🏷️</span>
        添加标签
      </div>
      <div class="menu-divider"></div>
      <div class="menu-item" @click="removeFromGroup">
        <span class="menu-icon">↩️</span>
        移出分组
      </div>
    </div>

    <!-- 移动到分组对话框 -->
    <div v-if="moveToGroupDialog.show" class="dialog-overlay" @click.self="closeMoveToGroupDialog">
      <div class="dialog">
        <h3>移动到分组</h3>
        <div class="group-list">
          <div
            v-for="group in groups"
            :key="group.id"
            class="group-option"
            @click="moveSessionToGroup(group.id)"
          >
            <span class="group-icon">{{ group.icon || '📁' }}</span>
            <span class="group-name">{{ group.name }}</span>
          </div>
        </div>
        <div class="dialog-actions">
          <button class="cancel-btn" @click="closeMoveToGroupDialog">取消</button>
        </div>
      </div>
    </div>

    <!-- 添加标签对话框 -->
    <div v-if="addTagDialog.show" class="dialog-overlay" @click.self="closeAddTagDialog">
      <div class="dialog">
        <h3>添加标签</h3>
        <div class="tag-list">
          <div
            v-for="tag in availableTags"
            :key="tag.id"
            class="tag-option"
            :class="{ selected: isTagSelected(tag.id) }"
            @click="toggleSessionTag(tag.id)"
          >
            <span class="tag-color" :style="{ backgroundColor: tag.color }"></span>
            <span class="tag-name">{{ tag.name }}</span>
          </div>
        </div>
        <div class="dialog-actions">
          <button class="save-btn" @click="closeAddTagDialog">完成</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { sessionGroupService } from '@/services/sessionGroupService'
import type { SessionGroup, SessionTag } from '@/types/sessionGroup'
import type { Session } from '@/types/session'

// Props
const props = defineProps<{
  sessions: Session[]
  activeSessionId?: string
}>()

// Emits
defineEmits<{
  'select-session': [sessionId: string]
}>()

// 状态
const groups = ref<SessionGroup[]>([])
const tags = ref<SessionTag[]>([])
const contextMenu = ref({
  show: false,
  x: 0,
  y: 0,
  sessionId: ''
})
const moveToGroupDialog = ref({
  show: false,
  sessionId: ''
})
const addTagDialog = ref({
  show: false,
  sessionId: ''
})

// 加载数据
onMounted(() => {
  loadData()
  document.addEventListener('click', closeContextMenu)
})

onUnmounted(() => {
  document.removeEventListener('click', closeContextMenu)
})

function loadData() {
  groups.value = sessionGroupService.getAllGroups()
  tags.value = sessionGroupService.getAllTags()
}

// 计算属性
const ungroupedSessions = computed(() => {
  return props.sessions.filter(session => {
    const groupId = sessionGroupService.getSessionGroup(session.id)
    return !groupId
  })
})

function getGroupSessions(groupId: string): Session[] {
  const sessionIds = sessionGroupService.getSessionsInGroup(groupId)
  return props.sessions.filter(session => sessionIds.includes(session.id))
}

function getSessionTags(sessionId: string): SessionTag[] {
  return sessionGroupService.getSessionTags(sessionId)
}

// 分组操作
function toggleGroup(groupId: string) {
  sessionGroupService.toggleGroupCollapse(groupId)
  loadData()
}

// 右键菜单
function showSessionContextMenu(sessionId: string, event: MouseEvent) {
  contextMenu.value = {
    show: true,
    x: event.clientX,
    y: event.clientY,
    sessionId
  }
}

function closeContextMenu() {
  contextMenu.value.show = false
}

function showMoveToGroupDialog() {
  moveToGroupDialog.value = {
    show: true,
    sessionId: contextMenu.value.sessionId
  }
  closeContextMenu()
}

function closeMoveToGroupDialog() {
  moveToGroupDialog.value.show = false
}

function moveSessionToGroup(groupId: string) {
  sessionGroupService.setSessionGroup(moveToGroupDialog.value.sessionId, groupId)
  closeMoveToGroupDialog()
  loadData()
}

function removeFromGroup() {
  sessionGroupService.setSessionGroup(contextMenu.value.sessionId, null)
  closeContextMenu()
  loadData()
}

// 标签操作
function showAddTagDialog() {
  addTagDialog.value = {
    show: true,
    sessionId: contextMenu.value.sessionId
  }
  closeContextMenu()
}

function closeAddTagDialog() {
  addTagDialog.value.show = false
}

const availableTags = computed(() => tags.value)

function isTagSelected(tagId: string): boolean {
  const sessionTags = sessionGroupService.getSessionTags(addTagDialog.value.sessionId)
  return sessionTags.some(tag => tag.id === tagId)
}

function toggleSessionTag(tagId: string) {
  const sessionId = addTagDialog.value.sessionId
  if (isTagSelected(tagId)) {
    sessionGroupService.removeSessionTag(sessionId, tagId)
  } else {
    sessionGroupService.addSessionTag(sessionId, tagId)
  }
  loadData()
}

// 工具函数
function formatTime(timestamp: number): string {
  const date = new Date(timestamp)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const days = Math.floor(diff / (1000 * 60 * 60 * 24))

  if (days === 0) {
    return '今天 ' + date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  } else if (days === 1) {
    return '昨天 ' + date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  } else if (days < 7) {
    return `${days}天前`
  } else {
    return date.toLocaleDateString('zh-CN')
  }
}
</script>

<style scoped>
.session-list-with-groups {
  height: 100%;
  overflow-y: auto;
}

.session-group {
  margin-bottom: 8px;
}

.group-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: var(--theme-panel-background);
  border-left: 3px solid transparent;
  cursor: pointer;
  user-select: none;
}

.group-header:hover {
  background: var(--theme-hover-background);
}

.group-icon {
  font-size: 16px;
}

.group-name {
  flex: 1;
  font-weight: 500;
  font-size: 13px;
  color: var(--theme-foreground);
}

.session-count {
  font-size: 12px;
  color: var(--theme-secondary-foreground);
}

.collapse-icon {
  font-size: 10px;
  color: var(--theme-secondary-foreground);
}

.sessions {
  display: flex;
  flex-direction: column;
}

.session-item {
  padding: 10px 12px 10px 32px;
  cursor: pointer;
  border-left: 3px solid transparent;
  transition: all 0.15s ease;
}

.session-item:hover {
  background: var(--theme-hover-background);
}

.session-item.active {
  background: var(--theme-selection-background);
  border-left-color: var(--theme-accent);
}

.session-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.session-name {
  font-size: 13px;
  color: var(--theme-foreground);
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-time {
  font-size: 11px;
  color: var(--theme-secondary-foreground);
  white-space: nowrap;
  margin-left: 8px;
}

.session-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 4px;
}

.tag {
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 11px;
  font-weight: 500;
}

/* 右键菜单 */
.context-menu {
  position: fixed;
  background: var(--theme-panel-background);
  border: 1px solid var(--theme-border);
  border-radius: 4px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
  z-index: 1000;
  min-width: 150px;
  padding: 4px 0;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  cursor: pointer;
  font-size: 13px;
  color: var(--theme-foreground);
}

.menu-item:hover {
  background: var(--theme-selection-background);
  color: var(--theme-selection-foreground);
}

.menu-icon {
  font-size: 14px;
}

.menu-divider {
  height: 1px;
  background: var(--theme-separator);
  margin: 4px 0;
}

/* 对话框 */
.dialog-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.dialog {
  background: var(--theme-background);
  border: 1px solid var(--theme-border);
  border-radius: 6px;
  padding: 20px;
  min-width: 300px;
  max-width: 400px;
  max-height: 60vh;
  overflow-y: auto;
}

.dialog h3 {
  margin: 0 0 16px 0;
  font-size: 15px;
  color: var(--theme-foreground);
}

.group-list,
.tag-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 16px;
}

.group-option,
.tag-option {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
}

.group-option:hover,
.tag-option:hover {
  background: var(--theme-hover-background);
}

.tag-option.selected {
  background: var(--theme-selection-background);
}

.tag-color {
  width: 12px;
  height: 12px;
  border-radius: 50%;
}

.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.cancel-btn,
.save-btn {
  padding: 6px 14px;
  border: none;
  border-radius: 3px;
  cursor: pointer;
  font-size: 13px;
}

.cancel-btn {
  background: var(--theme-panel-background);
  color: var(--theme-foreground);
}

.cancel-btn:hover {
  background: var(--theme-hover-background);
}

.save-btn {
  background: var(--theme-accent);
  color: var(--theme-selection-foreground);
}

.save-btn:hover {
  background: var(--theme-accent);
}
</style>


