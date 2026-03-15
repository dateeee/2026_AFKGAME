<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'

const router = useRouter()
const authStore = useAuthStore()

const email = ref('')
const password = ref('')
const mode = ref<'login' | 'register'>('login')
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
  <div class="login-container">
    <h1 class="game-title">AFK GAME</h1>
    <p class="subtitle">放置系ファンタジーRPG</p>

    <!-- ゲスト開始 -->
    <button
      class="btn btn-guest"
      :disabled="authStore.loading"
      @click="handleGuestStart"
    >
      {{ authStore.loading ? '準備中...' : 'ゲストで始める' }}
    </button>

    <div class="divider"><span>または</span></div>

    <!-- モード切替タブ -->
    <div class="tabs">
      <button
        :class="['tab', { active: mode === 'login' }]"
        @click="mode = 'login'"
      >
        ログイン
      </button>
      <button
        :class="['tab', { active: mode === 'register' }]"
        @click="mode = 'register'"
      >
        新規登録
      </button>
    </div>

    <!-- フォーム -->
    <form class="auth-form" @submit.prevent="handleSubmit">
      <div v-if="mode === 'register'" class="form-group">
        <label for="displayName">表示名</label>
        <input
          id="displayName"
          v-model="displayName"
          type="text"
          placeholder="冒険者"
        />
      </div>

      <div class="form-group">
        <label for="email">メールアドレス</label>
        <input
          id="email"
          v-model="email"
          type="email"
          required
          placeholder="example@mail.com"
        />
      </div>

      <div class="form-group">
        <label for="password">パスワード</label>
        <input
          id="password"
          v-model="password"
          type="password"
          required
          minlength="8"
          placeholder="8文字以上"
        />
      </div>

      <p v-if="authStore.error" class="error-message">{{ authStore.error }}</p>

      <button
        type="submit"
        class="btn btn-primary"
        :disabled="authStore.loading"
      >
        {{ authStore.loading ? '処理中...' : (mode === 'login' ? 'ログイン' : '登録') }}
      </button>
    </form>
  </div>
</template>

<style scoped>
.login-container {
  max-width: 400px;
  margin: 60px auto;
  padding: 24px;
  text-align: center;
}

.game-title {
  font-size: 2rem;
  margin-bottom: 4px;
}

.subtitle {
  color: #888;
  margin-bottom: 32px;
}

.btn {
  display: block;
  width: 100%;
  padding: 12px;
  border: none;
  border-radius: 8px;
  font-size: 1rem;
  cursor: pointer;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-guest {
  background: #4a9eff;
  color: #fff;
}

.btn-guest:hover:not(:disabled) {
  background: #3a8eef;
}

.btn-primary {
  background: #2c7a2c;
  color: #fff;
  margin-top: 8px;
}

.btn-primary:hover:not(:disabled) {
  background: #1f6a1f;
}

.divider {
  display: flex;
  align-items: center;
  margin: 24px 0;
  color: #888;
}

.divider::before,
.divider::after {
  content: '';
  flex: 1;
  border-bottom: 1px solid #444;
}

.divider span {
  padding: 0 12px;
  font-size: 0.875rem;
}

.tabs {
  display: flex;
  gap: 0;
  margin-bottom: 16px;
}

.tab {
  flex: 1;
  padding: 8px;
  border: 1px solid #444;
  background: transparent;
  color: #aaa;
  cursor: pointer;
  font-size: 0.875rem;
}

.tab:first-child {
  border-radius: 6px 0 0 6px;
}

.tab:last-child {
  border-radius: 0 6px 6px 0;
}

.tab.active {
  background: #333;
  color: #fff;
  border-color: #666;
}

.auth-form {
  text-align: left;
}

.form-group {
  margin-bottom: 12px;
}

.form-group label {
  display: block;
  margin-bottom: 4px;
  font-size: 0.875rem;
  color: #ccc;
}

.form-group input {
  width: 100%;
  padding: 10px;
  border: 1px solid #444;
  border-radius: 6px;
  background: #1a1a1a;
  color: #fff;
  font-size: 1rem;
  box-sizing: border-box;
}

.error-message {
  color: #ff6b6b;
  font-size: 0.875rem;
  margin: 8px 0;
}
</style>
