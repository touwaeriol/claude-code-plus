<template>
  <div class="display-item-renderer">
    <!-- 用户消息 -->
    <UserMessageBubble
      v-if="item.displayType === 'userMessage'"
      :message="item as any"
    />

    <!-- AI 文本回复 -->
    <AssistantTextDisplay
      v-else-if="item.displayType === 'assistantText'"
      :message="item"
    />

    <!-- 思考内容 -->
    <ThinkingDisplay
      v-else-if="item.displayType === 'thinking'"
      :thinking="item"
    />

    <!-- 工具调用 -->
    <template v-else-if="item.displayType === 'toolCall'">
      <ToolCallDisplay :tool-call="item" />
      <!-- 内联权限授权 UI -->
      <ToolPermissionInline
        v-if="pendingPermission"
        :permission="pendingPermission"
        @allow="handleAllow"
        @allowWithUpdate="handleAllowWithUpdate"
        @deny="handleDeny"
      />
    </template>

    <!-- 系统消息 -->
    <SystemMessageDisplay
      v-else-if="item.displayType === 'systemMessage'"
      :message="item"
    />

    <!-- 错误结果 -->
    <ErrorResultDisplay
      v-else-if="item.displayType === 'errorResult'"
      :error-message="item.message"
    />

    <!-- 未知类型 -->
    <div
      v-else
      class="unknown-item"
    >
      <span>Unknown item displayType: {{ (item as any).displayType ?? 'undefined' }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { DisplayItem } from '@/types/display'
import type { PermissionUpdate } from '@/types/permission'
import { useSessionStore } from '@/stores/sessionStore'
import UserMessageBubble from './UserMessageBubble.vue'
import AssistantTextDisplay from './AssistantTextDisplay.vue'
import ThinkingDisplay from './ThinkingDisplay.vue'
import ToolCallDisplay from './ToolCallDisplay.vue'
import SystemMessageDisplay from './SystemMessageDisplay.vue'
import ErrorResultDisplay from './ErrorResultDisplay.vue'
import ToolPermissionInline from '@/components/tools/ToolPermissionInline.vue'

interface Props {
  source: DisplayItem
}

const props = defineProps<Props>()
const sessionStore = useSessionStore()

const item = computed(() => props.source)

// 获取当前工具调用对应的权限请求
const pendingPermission = computed(() => {
  if (item.value.displayType !== 'toolCall') return null
  return sessionStore.getPermissionForToolCall(item.value.id)
})

function handleAllow() {
  if (pendingPermission.value) {
    sessionStore.respondPermission(pendingPermission.value.id, { approved: true })
  }
}

function handleAllowWithUpdate(update: PermissionUpdate) {
  if (pendingPermission.value) {
    sessionStore.respondPermission(pendingPermission.value.id, {
      approved: true,
      permissionUpdate: update
    })
  }
}

function handleDeny(reason: string) {
  if (pendingPermission.value) {
    sessionStore.respondPermission(pendingPermission.value.id, {
      approved: false,
      denyReason: reason || undefined
    })
  }
}
</script>

<style scoped>
.display-item-renderer {
  width: 100%;
  /* DynamicScroller 高度计算不包含外边距，把间距放在内边距避免重叠 */
  padding: 2px 0;
  box-sizing: border-box;
  min-height: 20px;
}

.unknown-item {
  padding: 6px 8px;
  background: #fff3cd;
  border: 1px solid #ffc107;
  border-radius: 6px;
  color: #856404;
  font-size: 13px;
  text-align: center;
}
</style>
