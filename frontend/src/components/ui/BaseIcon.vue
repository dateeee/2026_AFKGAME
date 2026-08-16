<script setup lang="ts">
import { ICON_PATHS, type IconName } from './icons'

const props = withDefaults(
  defineProps<{
    name: IconName
    size?: number | string
  }>(),
  { size: 20 },
)
</script>

<template>
  <!-- 描画するのは自前の静的定数（icons.ts の SVG パス）だけで、ユーザー・サーバー由来の
       文字列は渡らない。component.md §3 #4 が認める v-html の唯一の例外 -->
  <!-- eslint-disable vue/no-v-html -->
  <svg
    class="app-icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.6"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    focusable="false"
    v-html="ICON_PATHS[props.name]"
  />
</template>

<style scoped>
.app-icon {
  display: block;
  flex: none;
}
</style>
