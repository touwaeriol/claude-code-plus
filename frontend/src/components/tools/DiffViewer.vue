<template>
  <div class="diff-viewer">
    <pre class="diff-code-block"><code><span v-for="(line, index) in diffLines" :key="index" :class="line.class" class="diff-line">{{ line.content }}</span></code></pre>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { diffLines as computeDiffLines } from 'diff'

interface Props {
  oldContent: string
  newContent: string
}

const props = defineProps<Props>()

interface DiffLine {
  content: string
  class: string
}

const diffLines = computed(() => {
  const parts = computeDiffLines(props.oldContent, props.newContent)
  const lines: DiffLine[] = []

  parts.forEach(part => {
    const lineClass = part.added ? 'diff-added' : part.removed ? 'diff-removed' : 'diff-unchanged'
    const partLines = part.value.split('\n')

    // 移除最后一个空行（如果存在）
    if (partLines[partLines.length - 1] === '') {
      partLines.pop()
    }

    partLines.forEach(line => {
      lines.push({
        content: line,
        class: lineClass
      })
    })
  })

  return lines
})
</script>

<style scoped>
.diff-viewer {
  border: 1px solid var(--theme-border, #e1e4e8);
  border-radius: 4px;
  overflow: hidden;
  background: var(--theme-background, white);
}

.diff-code-block {
  margin: 0;
  padding: 12px;
  font-size: 12px;
  font-family: 'Consolas', 'Monaco', monospace;
}

.diff-code-block code {
  display: block;
}

/* 每一行 Diff */
.diff-line {
  display: block;
  white-space: pre;
}

.diff-added {
  background-color: #e6ffed;
  color: #22863a;
}

.diff-removed {
  background-color: #ffeef0;
  color: #d73a49;
}

.diff-unchanged {
  opacity: 0.7;
}

/* Dark theme adaptations */
html.dark .diff-added {
  background-color: rgba(46, 160, 67, 0.15);
  color: #7ee787;
}

html.dark .diff-removed {
  background-color: rgba(248, 81, 73, 0.15);
  color: #ffa198;
}
</style>

