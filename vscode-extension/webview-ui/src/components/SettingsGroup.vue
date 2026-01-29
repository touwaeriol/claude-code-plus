<template>
  <el-card class="settings-group" shadow="never">
    <template #header>
      <span class="group-title">{{ title }}</span>
    </template>
    <slot />
  </el-card>
</template>

<script setup lang="ts">
interface Props {
  title: string
}

defineProps<Props>()
</script>

<style scoped>
.settings-group {
  margin-bottom: 20px;
}

.group-title {
  font-size: 12px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
</style>
