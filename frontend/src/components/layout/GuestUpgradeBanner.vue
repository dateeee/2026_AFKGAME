<script setup lang="ts">
import { ref } from 'vue'
import { useAuthStore } from '@/stores/authStore'
import BaseIcon from '@/components/ui/BaseIcon.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseField from '@/components/ui/BaseField.vue'
import BaseModal from '@/components/ui/BaseModal.vue'
import BaseTextInput from '@/components/ui/BaseTextInput.vue'

/**
 * ゲスト→本登録の導線（ui_onboarding.md §ゲスト→本登録バナー）。
 * ゲストプレイ中は常時表示し、非表示にはできない。
 * [登録] は新規登録ではなく `POST /api/auth/link-account` を呼び、
 * ゲストのゲームデータをそのまま引き継ぐ。本登録が済むとバナーは自動で消える。
 */
const authStore = useAuthStore()

const open = ref(false)
const email = ref('')
const password = ref('')

function openForm() {
  email.value = ''
  password.value = ''
  authStore.error = null
  open.value = true
}

async function submit() {
  try {
    await authStore.linkAccount(email.value, password.value)
    open.value = false
  } catch {
    // エラーは authStore.error に格納済み（フォームは開いたままにする）
  }
}
</script>

<template>
  <div v-if="authStore.isAuthenticated && authStore.isGuest" class="guest-banner">
    <BaseIcon name="alert" :size="16" />
    <span class="guest-banner-text">データを守るためにアカウント登録しましょう</span>
    <BaseButton variant="primary" size="sm" @click="openForm">登録</BaseButton>
  </div>

  <BaseModal :open="open" title="アカウント登録" @close="open = false">
    <form id="guest-upgrade-form" class="guest-form" @submit.prevent="submit">
      <p class="guest-form-note">
        いまのゲームデータを引き継いだまま、メールアドレスで続きを遊べるようになります。
      </p>

      <BaseField label="メールアドレス">
        <template #default="{ id }">
          <BaseTextInput
            :id="id"
            v-model="email"
            type="email"
            required
            autocomplete="email"
            placeholder="you@example.com"
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
            autocomplete="new-password"
          />
        </template>
      </BaseField>

      <p v-if="authStore.error" class="guest-form-error" role="alert">{{ authStore.error }}</p>
    </form>

    <template #footer>
      <BaseButton variant="ghost" :disabled="authStore.loading" @click="open = false">
        あとで
      </BaseButton>
      <BaseButton
        type="submit"
        form="guest-upgrade-form"
        variant="primary"
        :disabled="authStore.loading"
      >
        {{ authStore.loading ? '登録中...' : '登録する' }}
      </BaseButton>
    </template>
  </BaseModal>
</template>

<style scoped>
.guest-banner {
  display: flex;
  align-items: center;
  gap: 0.625rem;
  padding: 0.625rem 0.875rem;
  margin-bottom: 1rem;
  background-color: var(--color-surface-2);
  border: 1px solid var(--color-line-soft);
  border-left: 3px solid var(--color-accent);
  border-radius: var(--radius-md);
  font-size: var(--text-label);
  color: var(--color-content);
}

.guest-banner-text {
  flex: 1;
  min-width: 0;
}

.guest-form {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.guest-form-note {
  margin: 0;
  font-size: var(--text-caption);
  color: var(--color-content-faint);
}

.guest-form-error {
  margin: 0;
  font-size: var(--text-label);
  color: var(--color-danger);
}
</style>
