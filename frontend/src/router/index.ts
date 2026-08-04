import {createRouter, createWebHistory} from 'vue-router'

import {useAuthStore} from '@/stores/auth'
import {applySeo} from '@/utils/seo'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('@/views/HomeView.vue'),
      meta: {
        title: '首页',
        description:
          'Khan Kiddo 是 AI 口语英语学习助手：分析对话语法与表达，生成诊断报告，帮你更自然地说英语。',
      },
    },
      {
          path: '/login',
          name: 'login',
          component: () => import('@/views/LoginView.vue'),
          meta: {
            title: '登录',
            description: '登录 Khan Kiddo，开始口语对话分析与英语学习复盘。',
            guestOnly: true,
          },
      },
      {
          path: '/register',
          name: 'register',
          component: () => import('@/views/RegisterView.vue'),
          meta: {
            title: '注册',
            description: '注册 Khan Kiddo 账号，免费体验 AI 口语英语诊断与学习助手。',
            guestOnly: true,
          },
      },
      {
          path: '/conversation/analyze',
          name: 'conversation-analyze',
          component: () => import('@/views/conversation/AnalyzeView.vue'),
          meta: {
            title: '对话分析',
            requiresAuth: true,
            robots: 'noindex, nofollow',
          },
      },
      {
          path: '/conversation/analyses',
          name: 'conversation-analyses',
          component: () => import('@/views/conversation/AnalysisListView.vue'),
          meta: {
            title: '分析历史',
            requiresAuth: true,
            robots: 'noindex, nofollow',
          },
      },
      {
          path: '/conversation/analyses/:id',
          name: 'conversation-analysis-detail',
          component: () => import('@/views/conversation/AnalysisDetailView.vue'),
          meta: {
            title: '分析详情',
            requiresAuth: true,
            robots: 'noindex, nofollow',
          },
      },
      {
          path: '/feedback',
          name: 'feedback',
          component: () => import('@/views/FeedbackView.vue'),
          meta: {
            title: '给我留言',
            description: '向 Khan Kiddo 团队留言反馈，分享使用体验与产品建议。',
          },
      },
      {
          path: '/review',
          name: 'review-center',
          component: () => import('@/views/review/ReviewCenterView.vue'),
          meta: {
            title: '复盘中心',
            requiresAuth: true,
            robots: 'noindex, nofollow',
          },
      },
      {
          path: '/conversation/grammar-rag',
          name: 'conversation-grammar-rag',
          redirect: {path: '/review', query: {chat: '1'}},
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
  applySeo({
    title: (to.meta.title as string) || 'Khan Kiddo',
    description: to.meta.description as string | undefined,
    path: to.path,
    robots: to.meta.robots as string | undefined,
  })
})

export default router
