<template>
  <div class="tool-display-showcase">
    <div class="showcase-header">
      <h1>🎨 工具展示系统</h1>
      <p class="subtitle">参考 Augment Code 设计的紧凑、直观的工具调用展示</p>
    </div>

    <div class="showcase-section">
      <h2>📄 文件操作工具</h2>
      <div class="tool-grid">
        <CompactToolCard
          v-for="(tool, index) in fileTools"
          :key="index"
          :display-info="tool"
          :is-expanded="expandedIndex === index"
          :has-details="true"
          @click="toggleExpand(index)"
        >
          <template #details>
            <div class="tool-details">
              <pre>{{ JSON.stringify(tool, null, 2) }}</pre>
            </div>
          </template>
        </CompactToolCard>
      </div>
    </div>

    <div class="showcase-section">
      <h2>💻 命令执行工具</h2>
      <div class="tool-grid">
        <CompactToolCard
          v-for="(tool, index) in commandTools"
          :key="`cmd-${index}`"
          :display-info="tool"
          :is-expanded="expandedIndex === `cmd-${index}`"
          :has-details="true"
          @click="toggleExpand(`cmd-${index}`)"
        >
          <template #details>
            <div class="tool-details">
              <pre>{{ JSON.stringify(tool, null, 2) }}</pre>
            </div>
          </template>
        </CompactToolCard>
      </div>
    </div>

    <div class="showcase-section">
      <h2>网络操作工具</h2>
      <div class="tool-grid">
        <CompactToolCard
          v-for="(tool, index) in networkTools"
          :key="`net-${index}`"
          :display-info="tool"
          :is-expanded="expandedIndex === `net-${index}`"
          :has-details="true"
          @click="toggleExpand(`net-${index}`)"
        >
          <template #details>
            <div class="tool-details">
              <pre>{{ JSON.stringify(tool, null, 2) }}</pre>
            </div>
          </template>
        </CompactToolCard>
      </div>
    </div>

    <div class="showcase-section">
      <h2>🤔 AI 功能工具</h2>
      <div class="tool-grid">
        <CompactToolCard
          v-for="(tool, index) in aiTools"
          :key="`ai-${index}`"
          :display-info="tool"
          :is-expanded="expandedIndex === `ai-${index}`"
          :has-details="true"
          @click="toggleExpand(`ai-${index}`)"
        >
          <template #details>
            <div class="tool-details">
              <pre>{{ JSON.stringify(tool, null, 2) }}</pre>
            </div>
          </template>
        </CompactToolCard>
      </div>
    </div>

    <div class="showcase-section">
      <h2>📋 任务管理工具</h2>
      <div class="tool-grid">
        <CompactToolCard
          v-for="(tool, index) in taskTools"
          :key="`task-${index}`"
          :display-info="tool"
          :is-expanded="expandedIndex === `task-${index}`"
          :has-details="true"
          @click="toggleExpand(`task-${index}`)"
        >
          <template #details>
            <div class="tool-details">
              <pre>{{ JSON.stringify(tool, null, 2) }}</pre>
            </div>
          </template>
        </CompactToolCard>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import CompactToolCard from '@/components/tools/CompactToolCard.vue'
import type { ToolDisplayInfo } from '@/utils/toolDisplayInfo'

const expandedIndex = ref<string | number | null>(null)

function toggleExpand(index: string | number) {
  expandedIndex.value = expandedIndex.value === index ? null : index
}

// 文件操作工具示例
const fileTools: ToolDisplayInfo[] = [
  {
    icon: '📖',
    actionType: 'Read',
    primaryInfo: 'main.ts:1-50',
    secondaryInfo: 'src/utils/',
    status: 'success',
  },
  {
    icon: '✏️',
    actionType: 'Edit',
    primaryInfo: 'config.ts:23',
    secondaryInfo: 'src/',
    lineChanges: '+2 -1',
    status: 'success',
  },
  {
    icon: '✏️',
    actionType: 'Write',
    primaryInfo: 'newFile.ts (45行)',
    secondaryInfo: 'src/components/',
    status: 'success',
  },
  {
    icon: '📝',
    actionType: 'MultiEdit',
    primaryInfo: 'App.vue (3处)',
    secondaryInfo: 'src/',
    status: 'pending',
    isInputLoading: false,
  },
  {
    icon: '🗑️',
    actionType: 'Remove',
    primaryInfo: 'oldFile.ts',
    secondaryInfo: 'src/deprecated/',
    status: 'error',
  },
]

// 命令执行工具示例
const commandTools: ToolDisplayInfo[] = [
  {
    icon: '💻',
    actionType: 'Bash',
    primaryInfo: 'npm install',
    secondaryInfo: '/project/root',
    status: 'success',
  },
  {
    icon: '🚀',
    actionType: 'Launch',
    primaryInfo: 'npm run dev',
    secondaryInfo: '/project',
    status: 'pending',
  },
  {
    icon: '📤',
    actionType: 'Output',
    primaryInfo: 'Build completed',
    secondaryInfo: '',
    status: 'success',
  },
  {
    icon: '🛑',
    actionType: 'Kill',
    primaryInfo: 'Process 1234',
    secondaryInfo: '',
    status: 'success',
  },
]

// 网络操作工具示例
const networkTools: ToolDisplayInfo[] = [
  {
    icon: '🔍',
    actionType: 'WebSearch',
    primaryInfo: '"Vue 3 composition API"',
    secondaryInfo: '',
    status: 'success',
  },
  {
    icon: '🌐',
    actionType: 'WebFetch',
    primaryInfo: 'github.com/vuejs/core',
    secondaryInfo: '',
    status: 'pending',
  },
]

// AI 功能工具示例
const aiTools: ToolDisplayInfo[] = [
  {
    icon: '🤔',
    actionType: 'Thinking',
    primaryInfo: 'Analyzing the problem...',
    secondaryInfo: 'Thought 1/5',
    status: 'pending',
  },
  {
    icon: '🧠',
    actionType: 'Codebase',
    primaryInfo: 'Retrieving from: <> Codebase',
    secondaryInfo: 'Vue component patterns',
    status: 'success',
  },
  {
    icon: '❓',
    actionType: 'AskUser',
    primaryInfo: 'Which approach do you prefer?',
    secondaryInfo: '+2 more',
    status: 'pending',
  },
  {
    icon: '💭',
    actionType: 'Remember',
    primaryInfo: 'User prefers TypeScript',
    secondaryInfo: '',
    status: 'success',
  },
]

// 任务管理工具示例
const taskTools: ToolDisplayInfo[] = [
  {
    icon: '✅',
    actionType: 'TodoWrite',
    primaryInfo: '5项任务',
    secondaryInfo: '',
    status: 'success',
  },
  {
    icon: '📋',
    actionType: 'Task',
    primaryInfo: 'Implement user authentication',
    secondaryInfo: '',
    status: 'pending',
  },
  {
    icon: '➕',
    actionType: 'AddTasks',
    primaryInfo: '3 new tasks',
    secondaryInfo: '',
    status: 'success',
  },
  {
    icon: '🔄',
    actionType: 'UpdateTasks',
    primaryInfo: '2 tasks updated',
    secondaryInfo: '',
    status: 'success',
  },
]
</script>

<style scoped>
.tool-display-showcase {
  max-width: 1200px;
  margin: 0 auto;
  padding: 40px 20px;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, sans-serif;
}

.showcase-header {
  text-align: center;
  margin-bottom: 60px;
}

.showcase-header h1 {
  font-size: 36px;
  font-weight: 700;
  margin: 0 0 12px 0;
  color: #1a1a1a;
}

.subtitle {
  font-size: 16px;
  color: #666;
  margin: 0;
}

.showcase-section {
  margin-bottom: 48px;
}

.showcase-section h2 {
  font-size: 24px;
  font-weight: 600;
  margin: 0 0 20px 0;
  color: #1a1a1a;
  padding-bottom: 12px;
  border-bottom: 2px solid #e0e0e0;
}

.tool-grid {
  display: flex;
  flex-direction: column;
  gap: 4px;
  background: #f8f9fa;
  padding: 16px;
  border-radius: 8px;
}

.tool-details {
  background: #f5f5f5;
  padding: 12px;
  border-radius: 4px;
  font-size: 12px;
  overflow-x: auto;
}

.tool-details pre {
  margin: 0;
  font-family: 'Monaco', 'Menlo', 'Consolas', monospace;
  color: #333;
}

/* 暗色主题 */
@media (prefers-color-scheme: dark) {
  .tool-display-showcase {
    background: #1e1e1e;
    color: #e0e0e0;
  }

  .showcase-header h1 {
    color: #e0e0e0;
  }

  .subtitle {
    color: #999;
  }

  .showcase-section h2 {
    color: #e0e0e0;
    border-bottom-color: #333;
  }

  .tool-grid {
    background: #252525;
  }

  .tool-details {
    background: #2a2a2a;
  }

  .tool-details pre {
    color: #ccc;
  }
}
</style>


