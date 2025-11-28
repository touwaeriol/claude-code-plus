<template>
  <div class="session-group-manager">
    <!-- 分组列表 -->
    <div class="groups-section">
      <div class="section-header">
        <h3>会话分组</h3>
        <button class="create-btn" @click="showCreateGroupDialog = true">
          <span class="icon">➕</span>
          新建分组
        </button>
      </div>

      <div class="groups-list">
        <div
          v-for="group in groups"
          :key="group.id"
          class="group-item"
          :style="{ borderLeftColor: group.color }"
        >
          <div class="group-header" @click="toggleGroup(group.id)">
            <span class="group-icon">{{ group.icon || '📁' }}</span>
            <span class="group-name">{{ group.name }}</span>
            <span class="session-count">({{ getSessionCount(group.id) }})</span>
            <span class="collapse-icon">{{ group.isCollapsed ? '▶' : '▼' }}</span>
          </div>

          <div v-if="!group.isCollapsed" class="group-content">
            <p v-if="group.description" class="group-description">{{ group.description }}</p>
            <div class="group-actions">
              <button class="action-btn" @click="editGroup(group)">编辑</button>
              <button class="action-btn danger" @click="deleteGroup(group.id)">删除</button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 标签列表 -->
    <div class="tags-section">
      <div class="section-header">
        <h3>会话标签</h3>
        <button class="create-btn" @click="showCreateTagDialog = true">
          <span class="icon">🏷️</span>
          新建标签
        </button>
      </div>

      <div class="tags-list">
        <div
          v-for="tag in tags"
          :key="tag.id"
          class="tag-item"
          :style="{ backgroundColor: tag.color + '20', borderColor: tag.color }"
        >
          <span class="tag-name" :style="{ color: tag.color }">{{ tag.name }}</span>
          <span class="session-count">({{ getTagSessionCount(tag.id) }})</span>
          <div class="tag-actions">
            <button class="action-btn-small" @click="editTag(tag)">✏️</button>
            <button class="action-btn-small" @click="deleteTag(tag.id)">🗑️</button>
          </div>
        </div>
      </div>
    </div>

    <!-- 创建/编辑分组对话框 -->
    <div v-if="showCreateGroupDialog || editingGroup" class="dialog-overlay" @click.self="closeGroupDialog">
      <div class="dialog">
        <h3>{{ editingGroup ? '编辑分组' : '创建分组' }}</h3>
        <div class="form-group">
          <label>分组名称</label>
          <input v-model="groupForm.name" type="text" placeholder="输入分组名称" />
        </div>
        <div class="form-group">
          <label>描述（可选）</label>
          <textarea v-model="groupForm.description" placeholder="输入分组描述"></textarea>
        </div>
        <div class="form-group">
          <label>颜色</label>
          <div class="color-picker">
            <div
              v-for="color in GROUP_COLORS"
              :key="color"
              class="color-option"
              :class="{ selected: groupForm.color === color }"
              :style="{ backgroundColor: color }"
              @click="groupForm.color = color"
            ></div>
          </div>
        </div>
        <div class="form-group">
          <label>图标（可选）</label>
          <div class="icon-picker">
            <div
              v-for="icon in GROUP_ICONS"
              :key="icon"
              class="icon-option"
              :class="{ selected: groupForm.icon === icon }"
              @click="groupForm.icon = icon"
            >
              {{ icon }}
            </div>
          </div>
        </div>
        <div class="dialog-actions">
          <button class="cancel-btn" @click="closeGroupDialog">取消</button>
          <button class="save-btn" @click="saveGroup">保存</button>
        </div>
      </div>
    </div>

    <!-- 创建/编辑标签对话框 -->
    <div v-if="showCreateTagDialog || editingTag" class="dialog-overlay" @click.self="closeTagDialog">
      <div class="dialog">
        <h3>{{ editingTag ? '编辑标签' : '创建标签' }}</h3>
        <div class="form-group">
          <label>标签名称</label>
          <input v-model="tagForm.name" type="text" placeholder="输入标签名称" />
        </div>
        <div class="form-group">
          <label>描述（可选）</label>
          <textarea v-model="tagForm.description" placeholder="输入标签描述"></textarea>
        </div>
        <div class="form-group">
          <label>颜色</label>
          <div class="color-picker">
            <div
              v-for="color in TAG_COLORS"
              :key="color"
              class="color-option"
              :class="{ selected: tagForm.color === color }"
              :style="{ backgroundColor: color }"
              @click="tagForm.color = color"
            ></div>
          </div>
        </div>
        <div class="dialog-actions">
          <button class="cancel-btn" @click="closeTagDialog">取消</button>
          <button class="save-btn" @click="saveTag">保存</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { sessionGroupService } from '@/services/sessionGroupService'
import type { SessionGroup, SessionTag } from '@/types/sessionGroup'
import { GROUP_COLORS, TAG_COLORS, GROUP_ICONS } from '@/types/sessionGroup'

// 状态
const groups = ref<SessionGroup[]>([])
const tags = ref<SessionTag[]>([])
const showCreateGroupDialog = ref(false)
const showCreateTagDialog = ref(false)
const editingGroup = ref<SessionGroup | null>(null)
const editingTag = ref<SessionTag | null>(null)

// 表单数据
const groupForm = ref({
  name: '',
  description: '',
  color: GROUP_COLORS[0],
  icon: GROUP_ICONS[0],
  order: 0
})

const tagForm = ref({
  name: '',
  description: '',
  color: TAG_COLORS[0]
})

// 加载数据
onMounted(() => {
  loadData()
})

function loadData() {
  groups.value = sessionGroupService.getAllGroups()
  tags.value = sessionGroupService.getAllTags()
}

// 分组操作
function toggleGroup(groupId: string) {
  sessionGroupService.toggleGroupCollapse(groupId)
  loadData()
}

function editGroup(group: SessionGroup) {
  editingGroup.value = group
  groupForm.value = {
    name: group.name,
    description: group.description || '',
    color: group.color,
    icon: group.icon || GROUP_ICONS[0],
    order: group.order
  }
}

function deleteGroup(groupId: string) {
  if (confirm('确定要删除这个分组吗？分组中的会话不会被删除。')) {
    sessionGroupService.deleteGroup(groupId)
    loadData()
  }
}

function saveGroup() {
  if (!groupForm.value.name.trim()) {
    alert('请输入分组名称')
    return
  }

  if (editingGroup.value) {
    sessionGroupService.updateGroup(editingGroup.value.id, {
      name: groupForm.value.name,
      description: groupForm.value.description || undefined,
      color: groupForm.value.color,
      icon: groupForm.value.icon,
      order: groupForm.value.order
    })
  } else {
    sessionGroupService.createGroup({
      name: groupForm.value.name,
      description: groupForm.value.description || undefined,
      color: groupForm.value.color,
      icon: groupForm.value.icon,
      order: groups.value.length,
      isCollapsed: false
    })
  }

  closeGroupDialog()
  loadData()
}

function closeGroupDialog() {
  showCreateGroupDialog.value = false
  editingGroup.value = null
  groupForm.value = {
    name: '',
    description: '',
    color: GROUP_COLORS[0],
    icon: GROUP_ICONS[0],
    order: 0
  }
}

// 标签操作
function editTag(tag: SessionTag) {
  editingTag.value = tag
  tagForm.value = {
    name: tag.name,
    description: tag.description || '',
    color: tag.color
  }
}

function deleteTag(tagId: string) {
  if (confirm('确定要删除这个标签吗？会话的标签关联将被移除。')) {
    sessionGroupService.deleteTag(tagId)
    loadData()
  }
}

function saveTag() {
  if (!tagForm.value.name.trim()) {
    alert('请输入标签名称')
    return
  }

  if (editingTag.value) {
    sessionGroupService.updateTag(editingTag.value.id, {
      name: tagForm.value.name,
      description: tagForm.value.description || undefined,
      color: tagForm.value.color
    })
  } else {
    sessionGroupService.createTag({
      name: tagForm.value.name,
      description: tagForm.value.description || undefined,
      color: tagForm.value.color
    })
  }

  closeTagDialog()
  loadData()
}

function closeTagDialog() {
  showCreateTagDialog.value = false
  editingTag.value = null
  tagForm.value = {
    name: '',
    description: '',
    color: TAG_COLORS[0]
  }
}

// 统计
function getSessionCount(groupId: string): number {
  return sessionGroupService.getSessionsInGroup(groupId).length
}

function getTagSessionCount(tagId: string): number {
  return sessionGroupService.getSessionsWithTag(tagId).length
}
</script>

<style scoped>
.session-group-manager {
  padding: 6px 8px;
  display: flex;
  flex-direction: column;
  gap: 24px;
  height: 100%;
  overflow-y: auto;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.section-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--ide-foreground, #24292e);
}

.create-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  background: var(--ide-accent, #0366d6);
  color: #ffffff;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
}

.create-btn:hover {
  background: var(--ide-accent-hover, #0256c7);
}

.icon {
  font-size: 14px;
}

/* 分组列表 */
.groups-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.group-item {
  border-left: 3px solid;
  background: var(--ide-background, #ffffff);
  border-radius: 4px;
  overflow: hidden;
}

.group-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
  cursor: pointer;
  user-select: none;
}

.group-header:hover {
  background: var(--ide-hover-background, #f6f8fa);
}

.group-icon {
  font-size: 18px;
}

.group-name {
  flex: 1;
  font-weight: 500;
  color: var(--ide-foreground, #24292e);
}

.session-count {
  font-size: 12px;
  color: var(--ide-text-secondary, #6a737d);
}

.collapse-icon {
  font-size: 12px;
  color: var(--ide-text-secondary, #6a737d);
}

.group-content {
  padding: 0 12px 12px 12px;
  border-top: 1px solid var(--ide-border, #e1e4e8);
}

.group-description {
  margin: 8px 0;
  font-size: 13px;
  color: var(--ide-text-secondary, #6a737d);
}

.group-actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}

.action-btn {
  padding: 4px 12px;
  background: var(--ide-button-secondary, #f6f8fa);
  color: var(--ide-foreground, #24292e);
  border: 1px solid var(--ide-border, #e1e4e8);
  border-radius: 3px;
  cursor: pointer;
  font-size: 12px;
}

.action-btn:hover {
  background: var(--ide-hover-background, #e1e4e8);
}

.action-btn.danger {
  background: var(--ide-error, #d73a49);
  color: white;
  border-color: var(--ide-error, #d73a49);
}

.action-btn.danger:hover {
  opacity: 0.8;
}

/* 标签列表 */
.tags-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border: 1px solid;
  border-radius: 16px;
  font-size: 13px;
}

.tag-name {
  font-weight: 500;
}

.tag-actions {
  display: flex;
  gap: 4px;
}

.action-btn-small {
  background: none;
  border: none;
  cursor: pointer;
  font-size: 14px;
  padding: 0;
  opacity: 0.7;
}

.action-btn-small:hover {
  opacity: 1;
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
  background: var(--vscode-editor-background);
  border: 1px solid var(--vscode-panel-border);
  border-radius: 6px;
  padding: 20px;
  min-width: 400px;
  max-width: 500px;
  max-height: 80vh;
  overflow-y: auto;
}

.dialog h3 {
  margin: 0 0 16px 0;
  font-size: 16px;
  color: var(--vscode-foreground);
}

.form-group {
  margin-bottom: 16px;
}

.form-group label {
  display: block;
  margin-bottom: 6px;
  font-size: 13px;
  font-weight: 500;
  color: var(--vscode-foreground);
}

.form-group input,
.form-group textarea {
  width: 100%;
  padding: 8px;
  background: var(--vscode-input-background);
  color: var(--vscode-input-foreground);
  border: 1px solid var(--vscode-input-border);
  border-radius: 3px;
  font-size: 13px;
  font-family: inherit;
}

.form-group textarea {
  min-height: 60px;
  resize: vertical;
}

.color-picker,
.icon-picker {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.color-option {
  width: 32px;
  height: 32px;
  border-radius: 4px;
  cursor: pointer;
  border: 2px solid transparent;
}

.color-option.selected {
  border-color: var(--vscode-focusBorder);
  box-shadow: 0 0 0 2px var(--vscode-editor-background);
}

.icon-option {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  cursor: pointer;
  border-radius: 4px;
  border: 2px solid transparent;
}

.icon-option.selected {
  border-color: var(--vscode-focusBorder);
  background: var(--vscode-list-activeSelectionBackground);
}

.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 20px;
}

.cancel-btn,
.save-btn {
  padding: 8px 16px;
  border: none;
  border-radius: 3px;
  cursor: pointer;
  font-size: 13px;
}

.cancel-btn {
  background: var(--vscode-button-secondaryBackground);
  color: var(--vscode-button-secondaryForeground);
}

.cancel-btn:hover {
  background: var(--vscode-button-secondaryHoverBackground);
}

.save-btn {
  background: var(--vscode-button-background);
  color: var(--vscode-button-foreground);
}

.save-btn:hover {
  background: var(--vscode-button-hoverBackground);
}
</style>


