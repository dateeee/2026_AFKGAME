<script setup lang="ts">
/**
 * 登録画面 — LoginViewの登録タブにリダイレクト
 * 仕様書では別画面だが、UXの観点からLoginViewに統合済み
 */
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

onMounted(() => {
  router.replace({ name: 'login', query: { mode: 'register' } })
})
</script>

<template>
  <div>リダイレクト中...</div>
</template>
