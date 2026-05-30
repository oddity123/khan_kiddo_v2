import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('@/views/HomeView.vue'),
      meta: { title: '首页' },
    },
  ],
})

router.afterEach((to) => {
  const title = (to.meta.title as string) || 'Khan Kiddo'
  document.title = `${title} · Khan Kiddo`
})

export default router
