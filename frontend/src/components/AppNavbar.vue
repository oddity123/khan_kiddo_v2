<script setup lang="ts">
import {ChatDotRound, Clock, Collection, DataAnalysis, House, Message, SwitchButton, Tickets, User, VideoPlay,} from '@element-plus/icons-vue'
import {ElMessage} from 'element-plus'
import {storeToRefs} from 'pinia'
import {onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {useRoute, useRouter} from 'vue-router'

import {useAuthStore} from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const { isAuthenticated, displayName } = storeToRefs(auth)

const analysisDropdownOpen = ref(false)
const moreOpen = ref(false)

function isActive(path: string) {
  return route.path === path
}

function isReviewActive() {
  return route.path === '/review'
}

function isReviewCardsActive() {
  return route.path === '/review/cards' || route.path.startsWith('/review/cards/')
}

function isAnalysisActive() {
  return route.path.startsWith('/conversation/')
}

function isMoreActive() {
  return isActive('/feedback') || isActive('/login') || isActive('/register')
}

function onPending(feature: string) {
  ElMessage.info(`${feature}功能迁移中，敬请期待`)
  moreOpen.value = false
}

function onAnalysisCommand(command: string) {
  if (command === 'analyze') {
    router.push('/conversation/analyze')
  } else if (command === 'history') {
    router.push('/conversation/analyses')
  }
}

function onAnalysisDropdownVisible(visible: boolean) {
  analysisDropdownOpen.value = visible
}

function closeMore() {
  moreOpen.value = false
}

function toggleMore() {
  moreOpen.value = !moreOpen.value
}

async function onLogout() {
  await auth.logout()
  moreOpen.value = false
  ElMessage.success('已退出登录')
  if (route.path !== '/') {
    await router.push('/')
  }
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    moreOpen.value = false
  }
}

watch(
  () => route.fullPath,
  () => {
    moreOpen.value = false
  },
)

onMounted(() => {
  window.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <!-- Desktop: floating top glass nav -->
  <header class="navbar-host navbar-host--desktop">
    <div class="kk-page-shell">
      <nav class="navbar-glass kk-glass kk-glass--nav" aria-label="主导航">
        <router-link to="/" class="navbar-brand">
          <img src="/icon.svg" alt="" class="navbar-brand-icon" />
          <span class="navbar-brand-text">Khan Kiddo AI英语学习助手</span>
        </router-link>

        <div class="navbar-nav">
          <router-link to="/" class="nav-link" :class="{ active: isActive('/') }">
            <el-icon><House /></el-icon>
            首页
          </router-link>

          <el-dropdown
            trigger="hover"
            placement="bottom-start"
            popper-class="kk-nav-dropdown"
            :show-arrow="false"
            :offset="8"
            @command="onAnalysisCommand"
            @visible-change="onAnalysisDropdownVisible"
          >
            <span
              class="nav-link nav-link--dropdown"
              :class="{ 'nav-link--open': analysisDropdownOpen, active: isAnalysisActive() }"
            >
              <el-icon><ChatDotRound /></el-icon>
              对话分析
              <span class="caret" />
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="analyze">
                  <el-icon><VideoPlay /></el-icon>
                  开始分析
                </el-dropdown-item>
                <el-dropdown-item divided command="history">
                  <el-icon><Clock /></el-icon>
                  查看历史记录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>

          <router-link
            to="/review"
            class="nav-link"
            :class="{ active: isReviewActive() }"
          >
            <el-icon><DataAnalysis /></el-icon>
            复盘中心
          </router-link>

          <router-link
            to="/review/cards"
            class="nav-link"
            :class="{ active: isReviewCardsActive() }"
          >
            <el-icon><Tickets /></el-icon>
            成长卡
          </router-link>

          <a class="nav-link" href="#" @click.prevent="onPending('句子笔记本')">
            <el-icon><Collection /></el-icon>
            笔记本
          </a>

          <router-link
            to="/feedback"
            class="nav-link"
            :class="{ active: isActive('/feedback') }"
          >
            <el-icon><Message /></el-icon>
            给我留言
          </router-link>

          <div class="navbar-auth">
            <template v-if="isAuthenticated">
              <span class="nav-link nav-link--muted">
                <el-icon><User /></el-icon>
                {{ displayName }}
              </span>
              <a class="nav-link" href="#" @click.prevent="onLogout">
                <el-icon><SwitchButton /></el-icon>
                退出
              </a>
            </template>
            <router-link
              v-else
              to="/login"
              class="nav-link nav-link--login"
            >
              <el-icon><User /></el-icon>
              登录
            </router-link>
          </div>
        </div>
      </nav>
    </div>
  </header>

  <!-- Mobile: standard bottom tab bar -->
  <nav class="tabbar" aria-label="底部导航">
    <div
      v-if="moreOpen"
      class="tabbar-backdrop"
      aria-hidden="true"
      @click="closeMore"
    />

    <div v-if="moreOpen" class="tabbar-sheet kk-glass" role="dialog" aria-label="更多">
      <router-link
        to="/conversation/analyses"
        class="tabbar-sheet-item"
        :class="{ active: isActive('/conversation/analyses') }"
        @click="closeMore"
      >
        <el-icon><Clock /></el-icon>
        分析历史
      </router-link>
      <a class="tabbar-sheet-item" href="#" @click.prevent="onPending('句子笔记本')">
        <el-icon><Collection /></el-icon>
        笔记本
      </a>
      <router-link
        to="/feedback"
        class="tabbar-sheet-item"
        :class="{ active: isActive('/feedback') }"
        @click="closeMore"
      >
        <el-icon><Message /></el-icon>
        给我留言
      </router-link>
      <div class="tabbar-sheet-divider" />
      <template v-if="isAuthenticated">
        <span class="tabbar-sheet-item tabbar-sheet-item--muted">
          <el-icon><User /></el-icon>
          {{ displayName }}
        </span>
        <button type="button" class="tabbar-sheet-item" @click="onLogout">
          <el-icon><SwitchButton /></el-icon>
          退出登录
        </button>
      </template>
      <router-link
        v-else
        to="/login"
        class="tabbar-sheet-item"
        @click="closeMore"
      >
        <el-icon><User /></el-icon>
        登录
      </router-link>
    </div>

    <div class="tabbar-bar kk-glass kk-glass--nav">
      <router-link
        to="/"
        class="tab-item"
        :class="{ active: isActive('/') }"
      >
        <el-icon :size="22"><House /></el-icon>
        <span>首页</span>
      </router-link>

      <router-link
        to="/conversation/analyze"
        class="tab-item"
        :class="{ active: isAnalysisActive() }"
      >
        <el-icon :size="22"><ChatDotRound /></el-icon>
        <span>分析</span>
      </router-link>

      <router-link
        to="/review"
        class="tab-item"
        :class="{ active: isReviewActive() }"
      >
        <el-icon :size="22"><DataAnalysis /></el-icon>
        <span>复盘</span>
      </router-link>

      <router-link
        to="/review/cards"
        class="tab-item"
        :class="{ active: isReviewCardsActive() }"
      >
        <el-icon :size="22"><Tickets /></el-icon>
        <span>成长卡</span>
      </router-link>

      <button
        type="button"
        class="tab-item"
        :class="{ active: moreOpen || isMoreActive() }"
        aria-haspopup="dialog"
        :aria-expanded="moreOpen"
        @click="toggleMore"
      >
        <el-icon :size="22"><User /></el-icon>
        <span>我的</span>
      </button>
    </div>
  </nav>
</template>

<style scoped>
.navbar-host {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 100;
  padding: 0.75rem 0 0;
  pointer-events: none;
}

.navbar-host .kk-page-shell,
.navbar-glass {
  pointer-events: auto;
}

.navbar-glass {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  min-height: 52px;
  padding: 0.35rem 0.85rem;
  transition: box-shadow var(--kk-duration-normal) ease;
}

.navbar-brand {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  font-weight: 700;
  font-size: 0.95rem;
  font-family: var(--kk-font-body);
  color: var(--kk-color-primary);
  text-decoration: none;
  white-space: nowrap;
  flex-shrink: 0;
  letter-spacing: -0.01em;
  padding: 0.35rem 0.5rem;
  border-radius: 10px;
}

.navbar-brand:hover {
  color: var(--kk-color-primary-soft);
  background: var(--kk-glass-subtle-bg);
}

.navbar-brand-icon {
  flex-shrink: 0;
  display: block;
  width: 35.04px;
  height: 35.04px;
  object-fit: contain;
}

.navbar-nav {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 0.2rem;
}

.navbar-auth {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 0.2rem;
}

.nav-link {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.42rem 0.72rem;
  border-radius: 10px;
  color: var(--kk-color-text-secondary);
  text-decoration: none;
  font-size: 0.9rem;
  font-family: var(--kk-font-body);
  font-weight: 500;
  cursor: pointer;
  border: none;
  background: transparent;
  white-space: nowrap;
  transition:
    color var(--kk-duration-normal) ease,
    background var(--kk-duration-normal) ease,
    box-shadow var(--kk-duration-normal) ease;
}

.nav-link:hover:not(.active) {
  color: var(--kk-color-primary);
  background: var(--kk-glass-hover-bg);
  box-shadow: inset 0 0 0 1px var(--kk-glass-hover-border);
}

.nav-link.active {
  color: #fff;
  font-weight: 600;
  background: linear-gradient(
    135deg,
    var(--kk-color-primary) 0%,
    var(--kk-color-primary-soft) 100%
  );
  box-shadow:
    0 4px 14px rgba(11, 26, 125, 0.32),
    inset 0 1px 0 rgba(255, 255, 255, 0.25);
}

.nav-link.active .el-icon {
  filter: drop-shadow(0 1px 1px rgba(0, 0, 0, 0.15));
}

.nav-link--login {
  background: rgba(11, 26, 125, 0.08);
}

.nav-link--login:hover {
  background: rgba(11, 26, 125, 0.12);
  color: var(--kk-color-primary);
}

.nav-link--muted {
  color: var(--kk-color-text-subtle);
  cursor: default;
}

.nav-link--muted:hover {
  background: transparent;
  box-shadow: none;
  color: var(--kk-color-text-subtle);
}

.nav-link--dropdown {
  outline: none;
}

.nav-link--open,
.nav-link--dropdown:hover {
  color: var(--kk-color-primary);
  background: var(--kk-glass-hover-bg);
  box-shadow: inset 0 0 0 1px var(--kk-glass-hover-border);
}

.caret {
  width: 0;
  height: 0;
  margin-left: 0.15rem;
  border-top: 4px solid currentColor;
  border-left: 4px solid transparent;
  border-right: 4px solid transparent;
  opacity: 0.7;
}

/* —— Mobile bottom tab bar —— */
.tabbar {
  display: none;
}

@media (max-width: 992px) {
  .navbar-host--desktop {
    display: none;
  }

  .tabbar {
    display: block;
    position: fixed;
    left: 0;
    right: 0;
    bottom: 0;
    z-index: 100;
    padding: 0 0.75rem calc(0.65rem + env(safe-area-inset-bottom, 0px));
    pointer-events: none;
  }

  .tabbar-backdrop {
    position: fixed;
    inset: 0;
    z-index: 101;
    background: rgba(20, 24, 36, 0.22);
    pointer-events: auto;
  }

  .tabbar-sheet {
    position: absolute;
    left: 0.75rem;
    right: 0.75rem;
    bottom: calc(100% + 0.45rem);
    z-index: 102;
    display: flex;
    flex-direction: column;
    gap: 0.2rem;
    padding: 0.45rem;
    border-radius: var(--kk-glass-dropdown-radius);
    pointer-events: auto;
    box-shadow:
      var(--kk-glass-nav-shadow),
      inset 0 1px 1px var(--kk-glass-nav-highlight-top);
  }

  .tabbar-sheet-item {
    display: flex;
    align-items: center;
    gap: 0.55rem;
    margin: 0;
    padding: 0.72rem 0.8rem;
    border: none;
    border-radius: var(--kk-glass-dropdown-item-radius);
    background: transparent;
    color: var(--kk-color-text-secondary);
    font-family: var(--kk-font-body);
    font-size: 0.92rem;
    font-weight: 500;
    text-decoration: none;
    cursor: pointer;
    text-align: left;
    width: 100%;
  }

  .tabbar-sheet-item:hover,
  .tabbar-sheet-item.active {
    color: var(--kk-color-primary);
    background: var(--kk-glass-hover-bg);
    box-shadow: inset 0 0 0 1px var(--kk-glass-hover-border);
  }

  .tabbar-sheet-item--muted {
    color: var(--kk-color-text-subtle);
    cursor: default;
  }

  .tabbar-sheet-item--muted:hover {
    background: transparent;
    box-shadow: none;
    color: var(--kk-color-text-subtle);
  }

  .tabbar-sheet-divider {
    height: 1px;
    margin: 0.25rem 0.5rem;
    background: var(--kk-glass-divider);
  }

  .tabbar-bar {
    position: relative;
    z-index: 103;
    display: grid;
    grid-template-columns: repeat(5, minmax(0, 1fr));
    align-items: stretch;
    gap: 0.1rem;
    min-height: 3.5rem;
    margin: 0;
    padding: 0.3rem 0.4rem;
    border-radius: var(--kk-glass-nav-radius);
    pointer-events: auto;
  }

  .tab-item {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 0.12rem;
    min-width: 0;
    padding: 0.28rem 0.2rem;
    border: none;
    border-radius: calc(var(--kk-glass-nav-radius) - 4px);
    background: transparent;
    color: var(--kk-color-text-muted);
    font-family: var(--kk-font-body);
    font-size: 0.68rem;
    font-weight: 600;
    line-height: 1.15;
    text-decoration: none;
    cursor: pointer;
    transition:
      color var(--kk-duration-normal) ease,
      background var(--kk-duration-normal) ease;
  }

  .tab-item .el-icon {
    font-size: 1.25rem;
  }

  .tab-item span {
    max-width: 100%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .tab-item.active {
    color: #fff;
    background: linear-gradient(
      135deg,
      var(--kk-color-primary) 0%,
      var(--kk-color-primary-soft) 100%
    );
    box-shadow:
      0 4px 12px rgba(11, 26, 125, 0.28),
      inset 0 1px 0 rgba(255, 255, 255, 0.22);
  }

  .tab-item.active .el-icon {
    filter: drop-shadow(0 1px 1px rgba(0, 0, 0, 0.12));
  }
}
</style>
