<script setup lang="ts">
import {ChatDotRound, Collection, House, Setting, SwitchButton, User} from '@element-plus/icons-vue'
import {ElMessage} from 'element-plus'
import {computed} from 'vue'
import {RouterView, useRoute, useRouter} from 'vue-router'

import {
  ADMIN_ANALYSES_PATH,
  ADMIN_BASE_PATH,
  ADMIN_KNOWLEDGE_PATH,
  ADMIN_USERS_PATH,
  isAdminDetailFromGlobalAnalyses,
} from '@/constants/admin'
import {useAuthStore} from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const navItems = [
  {path: ADMIN_BASE_PATH, label: '概览', icon: House, exact: true},
  {path: ADMIN_USERS_PATH, label: '用户管理', icon: User, exact: false},
  {path: ADMIN_ANALYSES_PATH, label: '对话管理', icon: ChatDotRound, exact: true},
  {path: ADMIN_KNOWLEDGE_PATH, label: '知识点字典', icon: Collection, exact: true},
]

function isNavActive(item: (typeof navItems)[number]) {
  const fromGlobalAnalyses = isAdminDetailFromGlobalAnalyses(route.query)
  const isAdminDetail = Boolean(route.meta.adminAnalysis)

  if (item.path === ADMIN_ANALYSES_PATH) {
    return route.path === ADMIN_ANALYSES_PATH || (isAdminDetail && fromGlobalAnalyses)
  }

  if (item.path === ADMIN_USERS_PATH) {
    if (route.path === ADMIN_USERS_PATH) {
      return true
    }
    if (route.path.startsWith(`${ADMIN_USERS_PATH}/`)) {
      return !(isAdminDetail && fromGlobalAnalyses)
    }
    return false
  }

  if (item.exact) {
    return route.path === item.path
  }
  return route.path === item.path || route.path.startsWith(`${item.path}/`)
}

const pageTitle = computed(() => {
  const matched = [...route.matched].reverse()
  for (const record of matched) {
    if (record.meta.title) {
      return String(record.meta.title)
    }
  }
  return '管理后台'
})

async function onLogout() {
  await auth.logout()
  ElMessage.success('已退出登录')
  await router.push('/login')
}
</script>

<template>
  <div class="admin-layout">
    <div class="kk-page-bg" aria-hidden="true"/>

    <aside class="admin-sidebar kk-glass kk-glass--panel">
      <div class="sidebar-head">
        <span class="sidebar-logo">
          <el-icon><Setting/></el-icon>
        </span>
        <div>
          <p class="sidebar-brand">Khan Kiddo</p>
          <p class="sidebar-sub">管理后台</p>
        </div>
      </div>

      <nav class="sidebar-nav">
        <router-link
            v-for="item in navItems"
            :key="item.path"
            :to="item.path"
            class="nav-item"
            :class="{ active: isNavActive(item) }"
        >
          <el-icon><component :is="item.icon"/></el-icon>
          {{ item.label }}
        </router-link>
      </nav>

      <div class="sidebar-foot">
        <router-link to="/" class="foot-link">
          <el-icon><House/></el-icon>
          返回站点
        </router-link>
        <button type="button" class="foot-link foot-link--button" @click="onLogout">
          <el-icon><SwitchButton/></el-icon>
          退出登录
        </button>
      </div>
    </aside>

    <div class="admin-body">
      <header class="admin-topbar kk-glass">
        <h1 class="admin-topbar-title">{{ pageTitle }}</h1>
        <span v-if="auth.displayName" class="admin-topbar-user">{{ auth.displayName }}</span>
      </header>

      <main class="admin-content">
        <RouterView/>
      </main>
    </div>
  </div>
</template>

<style scoped>
.admin-layout {
  display: flex;
  min-height: 100dvh;
  position: relative;
  overflow-x: hidden;
}

.admin-sidebar {
  position: fixed;
  top: 0;
  left: 0;
  bottom: 0;
  z-index: 20;
  width: 220px;
  display: flex;
  flex-direction: column;
  padding: 1.25rem 0.85rem;
  border-radius: 0;
  border-right: 1px solid var(--kk-glass-divider);
}

.sidebar-head {
  display: flex;
  align-items: center;
  gap: 0.65rem;
  padding: 0.35rem 0.5rem 1.25rem;
  border-bottom: 1px solid var(--kk-glass-divider);
  margin-bottom: 0.85rem;
}

.sidebar-logo {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: linear-gradient(135deg, var(--kk-color-primary) 0%, var(--kk-color-primary-soft) 100%);
  color: #fff;
}

.sidebar-brand {
  margin: 0;
  font-family: var(--kk-font-display);
  font-size: 0.95rem;
  font-weight: 700;
  color: var(--kk-color-primary);
  line-height: 1.2;
}

.sidebar-sub {
  margin: 0.1rem 0 0;
  font-size: 0.75rem;
  color: var(--kk-color-text-subtle);
}

.sidebar-nav {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.nav-item {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.55rem 0.75rem;
  border-radius: 10px;
  font-size: 0.9rem;
  font-weight: 500;
  color: var(--kk-color-text-secondary);
  text-decoration: none;
  transition:
    color var(--kk-duration-normal) ease,
    background var(--kk-duration-normal) ease;
}

.nav-item:hover {
  color: var(--kk-color-primary);
  background: var(--kk-glass-hover-bg);
}

.nav-item.active {
  color: #fff;
  font-weight: 600;
  background: linear-gradient(
    135deg,
    var(--kk-color-primary) 0%,
    var(--kk-color-primary-soft) 100%
  );
  box-shadow: 0 4px 14px rgba(11, 26, 125, 0.28);
}

.sidebar-foot {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  padding-top: 0.85rem;
  border-top: 1px solid var(--kk-glass-divider);
}

.foot-link {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  padding: 0.45rem 0.75rem;
  border-radius: 8px;
  font-size: 0.82rem;
  color: var(--kk-color-text-muted);
  text-decoration: none;
  background: transparent;
  border: none;
  cursor: pointer;
  font-family: inherit;
  text-align: left;
}

.foot-link:hover {
  color: var(--kk-color-primary);
  background: var(--kk-glass-subtle-bg);
}

.admin-body {
  flex: 1;
  margin-left: 220px;
  min-width: 0;
  display: flex;
  flex-direction: column;
  position: relative;
  z-index: 1;
}

.admin-topbar {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.85rem 1.5rem;
  border-radius: 0;
  border-bottom: 1px solid var(--kk-glass-divider);
}

.admin-topbar-title {
  margin: 0;
  font-family: var(--kk-font-display);
  font-size: 1.15rem;
  font-weight: 700;
  color: var(--kk-color-primary);
}

.admin-topbar-user {
  font-size: 0.85rem;
  color: var(--kk-color-text-subtle);
}

.admin-content {
  flex: 1;
  padding: 1.25rem 1.5rem 2rem;
}

@media (max-width: 768px) {
  .admin-sidebar {
    width: 100%;
    position: relative;
    height: auto;
    flex-direction: row;
    flex-wrap: wrap;
    align-items: center;
    padding: 0.75rem;
    gap: 0.5rem;
  }

  .sidebar-head {
    border-bottom: none;
    margin-bottom: 0;
    padding-bottom: 0;
  }

  .sidebar-nav {
    flex-direction: row;
    flex-wrap: wrap;
    flex: unset;
  }

  .sidebar-foot {
    flex-direction: row;
    border-top: none;
    padding-top: 0;
    margin-left: auto;
  }

  .admin-body {
    margin-left: 0;
  }

  .admin-layout {
    flex-direction: column;
  }
}
</style>
