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
      {
          path: '/register',
          name: 'register',
          component: () => import('@/views/RegisterView.vue'),
          meta: {title: '注册', guestOnly: true},
      },
      {
          path: '/conversation/analyze',
          name: 'conversation-analyze',
          component: () => import('@/views/conversation/AnalyzeView.vue'),
          meta: {title: '对话分析', requiresAuth: true},
      },
      {
          path: '/conversation/analyses',
          name: 'conversation-analyses',
          component: () => import('@/views/conversation/AnalysisListView.vue'),
          meta: {title: '分析历史', requiresAuth: true},
      },
      {
          path: '/conversation/analyses/:id',
          name: 'conversation-analysis-detail',
          component: () => import('@/views/conversation/AnalysisDetailView.vue'),
          meta: {title: '分析详情', requiresAuth: true},
      },
      {
          path: '/feedback',
          name: 'feedback',
          component: () => import('@/views/FeedbackView.vue'),
          meta: {title: '给我留言'},
      },
      {
          path: '/conversation/grammar-rag',
          name: 'conversation-grammar-rag',
          component: () => import('@/views/conversation/GrammarRagView.vue'),
          meta: {title: '语法复盘', requiresAuth: true, immersive: true},
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
    if (to.meta.requiresAuth && !auth.isAuthenticated) {
        return {path: '/login', query: {redirect: to.fullPath}}
    }
    return true
})

router.afterEach((to) => {
  const title = (to.meta.title as string) || 'Khan Kiddo'
  document.title = `${title} · Khan Kiddo`
})

export default router
