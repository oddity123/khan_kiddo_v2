import {createRouter, createWebHistory} from 'vue-router'

import {useAuthStore} from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('@/views/HomeView.vue'),
      meta: { title: '首页' },
    },
      {
          path: '/login',
          name: 'login',
          component: () => import('@/views/LoginView.vue'),
          meta: {title: '登录', guestOnly: true},
      },
  ],
})

router.beforeEach(async (to) => {
    const auth = useAuthStore()
    if (!auth.initialized) {
        await auth.initialize()
    }
    if (to.meta.guestOnly && auth.isAuthenticated) {
        return {path: '/'}
    }
    return true
})

router.afterEach((to) => {
  const title = (to.meta.title as string) || 'Khan Kiddo'
  document.title = `${title} · Khan Kiddo`
})

export default router
