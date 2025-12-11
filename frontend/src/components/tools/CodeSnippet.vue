<template>
  <div
    class="code-snippet-wrapper"
    :class="{ 'with-line-numbers': showLineNumbers }"
    :style="{ '--start-line': startLine }"
  >
    <div class="code-container" ref="codeContainer">
      <div class="code-content" v-html="highlightedCode"></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { highlightService } from '@/services/highlightService'

interface Props {
  code: string
  language: string
  startLine?: number
  showLineNumbers?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  startLine: 1,
  showLineNumbers: true
})

const highlightedCode = ref('')
const codeContainer = ref<HTMLDivElement>()

// 高亮代码
async function highlight() {
  if (!props.code) {
    highlightedCode.value = ''
    return
  }

  try {
    const html = await highlightService.highlight(props.code, props.language)
    highlightedCode.value = html
  } catch (error) {
    console.warn('Failed to highlight code:', error)
    highlightedCode.value = `<pre><code>${escapeHtml(props.code)}</code></pre>`
  }
}

function escapeHtml(text: string): string {
  const div = document.createElement('div')
  div.textContent = text
  return div.innerHTML
}

onMounted(highlight)

watch(() => [props.code, props.language], highlight)
</script>

<style scoped>
.code-snippet-wrapper {
  background: var(--theme-panel-background, #f6f8fa);
  border: 1px solid var(--theme-border, #e1e4e8);
  border-radius: 4px;
  overflow: hidden;
  width: 100%;
  text-decoration: none !important;
}

.code-container {
  width: 100%;
  max-height: var(--code-max-height, none);
  overflow: auto;
  font-family: 'Consolas', 'Monaco', 'Menlo', 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.5;
}

.code-content {
  min-width: 0;
  overflow-x: visible;
  padding: 8px 12px;
}

/* Shiki 生成的 pre/code 样式 */
.code-content :deep(pre) {
  margin: 0;
  padding: 0;
  background: transparent !important;
  overflow: visible;
  width: 100%;
  min-width: 100%;
}

.code-content :deep(code) {
  display: flex;
  flex-direction: column;
  font-family: inherit;
  font-size: 12px;
  line-height: 1.5;
  padding: 0 !important;
  margin: 0;
  width: 100%;
  min-width: 100%;
  /* CSS Counter：从 --start-line 开始计数 */
  counter-reset: line-number calc(var(--start-line, 1) - 1);
}

.code-content :deep(.line) {
  display: block;
  min-height: 18px; /* line-height 1.5 * font-size 12px = 18px */
}

/* 行号样式：使用 CSS Counter 自动生成 */
.with-line-numbers .code-content :deep(.line)::before {
  counter-increment: line-number;
  content: counter(line-number);
  display: inline-block;
  width: 3em;
  margin-right: 12px;
  padding-right: 8px;
  text-align: right;
  color: var(--theme-secondary-foreground, #999);
  border-right: 1px solid var(--theme-border, #e1e4e8);
  user-select: none;
  /* 防止行号影响代码缩进 */
  margin-left: -3em;
  padding-left: 0;
}

/* 有行号时，代码内容需要左边距给行号留空间 */
.with-line-numbers .code-content :deep(.line) {
  padding-left: calc(3em + 20px);
}

/* 去除下划线 */
.code-content :deep(*) {
  text-decoration: none !important;
}

.code-snippet-wrapper :deep(*) {
  text-decoration: none !important;
}
</style>
