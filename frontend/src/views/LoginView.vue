<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const email = ref('')
const password = ref('')
// RegisterView から ?mode=register で飛んできた場合は登録フォームを開いた状態にする
const mode = ref<'login' | 'register'>(route.query.mode === 'register' ? 'register' : 'login')
const displayName = ref('')

async function handleGuestStart() {
  try {
    await authStore.startAsGuest()
    router.push('/')
  } catch {
    // エラーはauthStore.errorに格納済み
  }
}

async function handleSubmit() {
  try {
    if (mode.value === 'register') {
      await authStore.register(email.value, password.value, displayName.value || undefined)
    } else {
      await authStore.loginWithEmail(email.value, password.value)
    }
    router.push('/')
  } catch {
    // エラーはauthStore.errorに格納済み
  }
}
</script>

<template>
  <div class="mx-auto max-w-[400px] mt-16 px-6 text-center">
    <!-- Title -->
    <h1 class="font-display text-[2.5rem] font-bold text-gold game-title-glow mb-1">
      AFK GAME
    </h1>
    <p class="text-text-muted text-sm mb-8">放置系ファンタジーRPG</p>

    <!-- Guest Start -->
    <button
      class="btn btn-primary w-full py-3 text-base"
      :disabled="authStore.loading"
      @click="handleGuestStart"
    >
      {{ authStore.loading ? '準備中...' : 'ゲストで始める' }}
    </button>

    <!-- Divider -->
    <div class="flex items-center my-6">
      <div class="flex-1 border-b border-border"></div>
      <span class="px-3 text-sm text-text-muted">または</span>
      <div class="flex-1 border-b border-border"></div>
    </div>

    <!-- Mode Tabs -->
    <div class="flex mb-4">
      <button
        :class="[
          'flex-1 py-2 text-sm border transition-all duration-150',
          'rounded-l-lg',
          mode === 'login'
            ? 'bg-bg-elevated text-text-bright border-border-glow'
            : 'bg-transparent text-text-muted border-border hover:bg-bg-secondary'
        ]"
        @click="mode = 'login'"
      >
        ログイン
      </button>
      <button
        :class="[
          'flex-1 py-2 text-sm border border-l-0 transition-all duration-150',
          'rounded-r-lg',
          mode === 'register'
            ? 'bg-bg-elevated text-text-bright border-border-glow'
            : 'bg-transparent text-text-muted border-border hover:bg-bg-secondary'
        ]"
        @click="mode = 'register'"
      >
        新規登録
      </button>
    </div>

    <!-- Form -->
    <form class="text-left" @submit.prevent="handleSubmit">
      <div v-if="mode === 'register'" class="mb-3">
        <label for="displayName" class="block mb-1 text-sm text-text">表示名</label>
        <input
          id="displayName"
          v-model="displayName"
          type="text"
          placeholder="冒険者"
          class="form-input"
        />
      </div>

      <div class="mb-3">
        <label for="email" class="block mb-1 text-sm text-text">メールアドレス</label>
        <input
          id="email"
          v-model="email"
          type="email"
          required
          placeholder="example@mail.com"
          class="form-input"
        />
      </div>

      <div class="mb-3">
        <label for="password" class="block mb-1 text-sm text-text">パスワード</label>
        <input
          id="password"
          v-model="password"
          type="password"
          required
          minlength="8"
          placeholder="8文字以上"
          class="form-input"
        />
      </div>

      <p v-if="authStore.error" class="text-danger-glow text-sm my-2">{{ authStore.error }}</p>

      <button
        type="submit"
        class="btn btn-secondary w-full py-3 text-base mt-2"
        :disabled="authStore.loading"
      >
        {{ authStore.loading ? '処理中...' : (mode === 'login' ? 'ログイン' : '登録') }}
      </button>
    </form>
  </div>
</template>

<style scoped>
.game-title-glow {
  text-shadow:
    0 0 20px rgba(139, 92, 246, 0.4),
    0 0 60px rgba(139, 92, 246, 0.15),
    0 2px 4px rgba(0, 0, 0, 0.5);
}

.form-input {
  width: 100%;
  padding: 0.625rem;
  border: 1px solid var(--color-border);
  border-radius: 0.375rem;
  background: var(--color-bg);
  color: var(--color-text-bright);
  font-size: 1rem;
  font-family: var(--font-body);
  transition: border-color 150ms;
}

.form-input:focus {
  border-color: var(--color-primary);
  outline: none;
}

.form-input::placeholder {
  color: var(--color-text-muted);
}
</style>
