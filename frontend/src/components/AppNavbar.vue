<script setup lang="ts">
import {ChatDotRound, Clock, Collection, House, Message, SwitchButton, User, VideoPlay,} from '@element-plus/icons-vue'
import {ElMessage} from 'element-plus'
import {storeToRefs} from 'pinia'
import {ref} from 'vue'
import {useRoute, useRouter} from 'vue-router'

import {useAuthStore} from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const { isAuthenticated, displayName } = storeToRefs(auth)

const mobileOpen = ref(false)
const analysisDropdownOpen = ref(false)

function isActive(path: string) {
  return route.path === path
}

function onPending(feature: string) {
  ElMessage.info(`${feature}功能迁移中，敬请期待`)
  mobileOpen.value = false
}

function onAnalysisCommand(command: string) {
  mobileOpen.value = false
  if (command === 'analyze') {
    router.push('/conversation/analyze')
  } else if (command === 'history') {
    router.push('/conversation/analyses')
  }
}

function onAnalysisDropdownVisible(visible: boolean) {
  analysisDropdownOpen.value = visible
}

async function onLogout() {
  await auth.logout()
  mobileOpen.value = false
  ElMessage.success('已退出登录')
  if (route.path !== '/') {
    await router.push('/')
  }
}
</script>

<template>
  <header class="navbar-host">
    <div class="kk-page-shell">
      <nav class="navbar-glass kk-glass kk-glass--nav" :class="{ 'navbar-glass--open': mobileOpen }">
        <router-link to="/" class="navbar-brand" @click="mobileOpen = false">
          <img src="/icon.svg" alt="" class="navbar-brand-icon" />
          <span class="navbar-brand-text">Khan Kiddo AI英语学习助手</span>
        </router-link>

        <button
          type="button"
          class="navbar-toggle"
          aria-label="切换导航"
          @click="mobileOpen = !mobileOpen"
        >
          <span /><span /><span />
        </button>

        <div class="navbar-nav" :class="{ open: mobileOpen }">
          <router-link to="/" class="nav-link" :class="{ active: isActive('/') }" @click="mobileOpen = false">
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
              :class="{ 'nav-link--open': analysisDropdownOpen }"
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

          <a class="nav-link" href="#" @click.prevent="onPending('句子笔记本')">
            <el-icon><Collection /></el-icon>
            笔记本
          </a>

          <router-link
              to="/langchain4j-learning"
              class="nav-link"
              :class="{ active: isActive('/langchain4j-learning') }"
              @click="mobileOpen = false"
          >
            <el-icon>
              <ChatDotRound/>
            </el-icon>
            LangChain for Java 学习
          </router-link>

          <router-link
              to="/feedback"
              class="nav-link"
              :class="{ active: isActive('/feedback') }"
              @click="mobileOpen = false"
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
              @click="mobileOpen = false"
            >
              <el-icon><User /></el-icon>
              登录
            </router-link>
          </div>
        </div>
      </nav>
    </div>
  </header>
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

.navbar-toggle {
  display: none;
  margin-left: auto;
  width: 36px;
  height: 36px;
  border: none;
  background: var(--kk-glass-subtle-bg-strong);
  border-radius: 10px;
  padding: 6px;
  cursor: pointer;
}

.navbar-toggle span {
  display: block;
  height: 2px;
  margin: 5px 0;
  background: var(--kk-color-text-secondary);
  border-radius: 1px;
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

@media (max-width: 992px) {
  .navbar-brand-text {
    font-size: 0.88rem;
  }

  .navbar-glass {
    flex-wrap: wrap;
    align-items: center;
    padding: 0.5rem 0.75rem;
  }

  .navbar-glass--open {
    padding-bottom: 0.75rem;
  }

  .navbar-toggle {
    display: block;
  }

  .navbar-nav {
    display: none;
    width: 100%;
    flex-direction: column;
    align-items: stretch;
    padding-top: 0.35rem;
  }

  .navbar-nav.open {
    display: flex;
  }

  .navbar-auth {
    margin-left: 0;
    flex-direction: column;
    align-items: stretch;
    border-top: 1px solid var(--kk-glass-divider);
    padding-top: 0.5rem;
    margin-top: 0.25rem;
  }

  .nav-link {
    width: 100%;
  }
}
</style>
