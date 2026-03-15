import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true },
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/views/RegisterView.vue'),
      meta: { public: true },
    },
    {
      path: '/',
      name: 'game',
      component: () => import('@/views/GameView.vue'),
    },
    {
      path: '/equipment',
      name: 'equipment',
      component: () => import('@/views/EquipmentView.vue'),
    },
    {
      path: '/shop',
      name: 'shop',
      component: () => import('@/views/ShopView.vue'),
    },
    {
      path: '/settings',
      name: 'settings',
      component: () => import('@/views/SettingsView.vue'),
    },
  ],
})

// ナビゲーションガード: 未認証ユーザーをログイン画面にリダイレクト
router.beforeEach(async (to) => {
  if (to.meta.public) return true

  const { useAuthStore } = await import('@/stores/authStore')
  const authStore = useAuthStore()

  if (authStore.isAuthenticated) return true

  // セッション復元を試行
  const restored = await authStore.restoreSession()
  if (restored) return true

  return { name: 'login' }
})

export default router
