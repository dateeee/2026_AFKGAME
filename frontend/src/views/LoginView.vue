<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseField from '@/components/ui/BaseField.vue'
import BaseTextInput from '@/components/ui/BaseTextInput.vue'

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
  <div class="login">
    <header class="login-head">
      <h1 class="login-title">AFK GAME</h1>
      <p class="login-sub">放置系ファンタジーRPG</p>
    </header>

    <!-- 最も多い導線を最初に、最も大きく置く -->
    <BaseButton
      variant="primary"
      size="lg"
      block
      :disabled="authStore.loading"
      @click="handleGuestStart"
    >
      {{ authStore.loading ? '準備中...' : 'ゲストで始める' }}
    </BaseButton>
    <p class="guest-note">アカウントは後から作成できます</p>

    <div class="divider"><span>または</span></div>

    <div class="tabs" role="tablist">
      <button
        v-for="t in [{ id: 'login', label: 'ログイン' }, { id: 'register', label: '新規登録' }] as const"
        :key="t.id"
        type="button"
        class="tab"
        :class="{ 'tab-active': mode === t.id }"
        role="tab"
        :aria-selected="mode === t.id"
        @click="mode = t.id"
      >
        {{ t.label }}
      </button>
    </div>

    <form class="form" @submit.prevent="handleSubmit">
      <BaseField v-if="mode === 'register'" label="表示名">
        <template #default="{ id }">
          <BaseTextInput :id="id" v-model="displayName" placeholder="冒険者" autocomplete="nickname" />
        </template>
      </BaseField>

      <BaseField label="メールアドレス">
        <template #default="{ id }">
          <BaseTextInput
            :id="id"
            v-model="email"
            type="email"
            required
            placeholder="example@mail.com"
            autocomplete="email"
          />
        </template>
      </BaseField>

      <BaseField label="パスワード" hint="8文字以上">
        <template #default="{ id }">
          <BaseTextInput
            :id="id"
            v-model="password"
            type="password"
            required
            :minlength="8"
            :autocomplete="mode === 'register' ? 'new-password' : 'current-password'"
          />
        </template>
      </BaseField>

      <p v-if="authStore.error" class="form-error" role="alert">{{ authStore.error }}</p>

      <BaseButton type="submit" variant="secondary" size="lg" block :disabled="authStore.loading">
        {{ authStore.loading ? '処理中...' : (mode === 'login' ? 'ログイン' : '登録') }}
      </BaseButton>
    </form>
  </div>
</template>

<style scoped>
.login {
  width: 100%;
  max-width: 22rem;
}

.login-head {
  text-align: center;
  margin-bottom: 2rem;
}

.login-title {
  font-family: var(--font-display);
  font-size: var(--text-display);
  font-weight: 700;
  letter-spacing: 0.14em;
  color: var(--color-accent-pale);
  /* 淡い金の残光。輪郭を強くしすぎない */
  text-shadow:
    0 0 24px rgba(201, 162, 39, 0.28),
    0 2px 6px rgba(0, 0, 0, 0.6);
}

.login-sub {
  margin-top: 0.5rem;
  font-size: var(--text-label);
  letter-spacing: 0.06em;
  color: var(--color-content-muted);
}

.guest-note {
  margin-top: 0.5rem;
  font-size: var(--text-caption);
  color: var(--color-content-faint);
  text-align: center;
}

.divider {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin: 1.75rem 0;
  color: var(--color-content-faint);
  font-size: var(--text-caption);
}

.divider::before,
.divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background-color: var(--color-line-soft);
}

.tabs {
  display: flex;
  gap: 0.25rem;
  padding: 0.25rem;
  margin-bottom: 1.25rem;
  background-color: var(--color-surface-1);
  border: 1px solid var(--color-line-soft);
  border-radius: var(--radius-lg);
}

.tab {
  flex: 1;
  min-height: 2.5rem;
  border: none;
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--color-content-muted);
  font-size: var(--text-label);
  font-weight: 600;
  cursor: pointer;
  transition: background-color var(--duration-fast) ease, color var(--duration-fast) ease;
}

.tab-active {
  background-color: var(--color-surface-3);
  color: var(--color-content-strong);
  box-shadow: inset 0 1px 0 rgba(242, 239, 228, 0.06);
}

.form {
  display: flex;
  flex-direction: column;
  gap: 0.875rem;
}

.form-error {
  font-size: var(--text-label);
  color: var(--color-danger-bright);
}
</style>
