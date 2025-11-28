<template>
  <Teleport to="body">
    <div
      v-if="visible && imageSrc"
      class="image-preview-overlay"
      @click.self="handleClose"
    >
      <div class="image-preview-modal">
        <button
          class="close-button"
          type="button"
          title="关闭"
          @click="handleClose"
        >
          ✕
        </button>
        <div class="image-wrapper">
          <img
            :src="imageSrc"
            :alt="imageAlt || '预览图片'"
            class="preview-image"
          />
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { watch, onUnmounted } from 'vue'

interface Props {
  visible: boolean
  imageSrc: string
  imageAlt?: string
}

interface Emits {
  (e: 'close'): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

function handleClose() {
  emit('close')
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') {
    handleClose()
  }
}

// 监听 visible 变化，添加/移除键盘事件
watch(() => props.visible, (visible) => {
  if (visible) {
    document.addEventListener('keydown', handleKeydown)
  } else {
    document.removeEventListener('keydown', handleKeydown)
  }
}, { immediate: true })

onUnmounted(() => {
  document.removeEventListener('keydown', handleKeydown)
})
</script>

<style>
/* 使用非 scoped 样式，因为 Teleport 会将元素移到 body */
.image-preview-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.7);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
}

.image-preview-modal {
  position: relative;
  max-width: 90vw;
  max-height: 90vh;
  background: #1e1e1e;
  border-radius: 6px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.5);
  padding: 4px;
  overflow: visible;
}

.image-preview-modal .image-wrapper {
  max-width: 80vw;
  max-height: 80vh;
}

.image-preview-modal .preview-image {
  display: block;
  max-width: 100%;
  max-height: 80vh;
  object-fit: contain;
  border-radius: 4px;
}

.image-preview-modal .close-button {
  position: absolute;
  top: 4px;
  right: 4px;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  border: none;
  background: rgba(0, 0, 0, 0.5);
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  line-height: 1;
  transition: background 0.2s ease;
  z-index: 1;
}

.image-preview-modal .close-button:hover {
  background: rgba(0, 0, 0, 0.8);
}
</style>












