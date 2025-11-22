<template>
  <div class="code-snippet-wrapper">
    <div class="code-container">
      <!-- 行号列 -->
      <div class="line-numbers" aria-hidden="true">
        <div v-for="(lineNum, index) in lineNumbers" :key="`line-${index}`" class="line-number">
          {{ lineNum }}
        </div>
      </div>
      <!-- 代码内容 -->
      <pre class="code-content"><code><div v-for="(line, index) in codeLines" :key="`code-${index}`" class="code-line">{{ line }}</div></code></pre>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  code: string
  language: string
  startLine?: number  // 起始行号，默认为 1
}

const props = withDefaults(defineProps<Props>(), {
  startLine: 1
})

const codeLines = computed(() => {
  if (!props.code) return []

  const lines = props.code.split('\n')

  // 去除尾部的空行（包括只有空格的行）
  while (lines.length > 0 && lines[lines.length - 1].trim() === '') {
    lines.pop()
  }

  // 如果所有行都是空的，返回空数组
  if (lines.length === 0 || lines.every(line => line.trim() === '')) {
    return []
  }

  return lines
})

// 生成行号数组
const lineNumbers = computed(() => {
  const start = props.startLine
  return codeLines.value.map((_, index) => start + index)
})
</script>

<style scoped>
.code-snippet-wrapper {
  background: var(--ide-panel-background, #f6f8fa);
  border: 1px solid var(--ide-border, #e1e4e8);
  border-radius: 4px;
  overflow: hidden;
}

.code-container {
  display: flex;
  max-height: 400px;
  overflow: auto;
}

/* 行号列 */
.line-numbers {
  flex-shrink: 0;
  padding: 12px 0;
  background: var(--ide-panel-background, #f6f8fa);
  border-right: 1px solid var(--ide-border, #e1e4e8);
  user-select: none; /* 整个行号列不可选中 */
  -webkit-user-select: none;
  -moz-user-select: none;
  -ms-user-select: none;
}

.line-number {
  padding: 0 1em;
  text-align: right;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 12px;
  line-height: 1.5;
  color: var(--ide-secondary-foreground, #999);
  min-width: 3em;
}

/* 代码内容 */
.code-content {
  flex: 1;
  margin: 0;
  padding: 12px;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 12px;
  overflow-x: auto;
}

.code-content code {
  display: block;
}

.code-line {
  line-height: 1.5;
  white-space: pre;
}

/* 暗色主题适配 */
.theme-dark .line-numbers {
  background: var(--ide-panel-background, #1e1e1e);
  border-right-color: var(--ide-border, rgba(255, 255, 255, 0.1));
}

.theme-dark .line-number {
  color: var(--ide-secondary-foreground, #666);
}
</style>

