<template>
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
</template>

<script setup lang="ts">
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
</script>

<style scoped>
.image-preview-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
}

.image-preview-modal {
  position: relative;
  max-width: 90vw;
  max-height: 90vh;
  background: #1e1e1e;
  border-radius: 8px;
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.6);
  padding: 32px 24px 24px;
}

.image-wrapper {
  max-width: 80vw;
  max-height: 70vh;
}

.preview-image {
  display: block;
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  border-radius: 4px;
}

.close-button {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  border: none;
  background: rgba(0, 0, 0, 0.5);
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  line-height: 1;
  transition: background 0.2s ease;
}

.close-button:hover {
  background: rgba(0, 0, 0, 0.8);
}
</style>







