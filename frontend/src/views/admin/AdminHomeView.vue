<script setup lang="ts">
import {ArrowRight, ChatDotRound, Collection, User} from '@element-plus/icons-vue'

import {ADMIN_ANALYSES_PATH, ADMIN_KNOWLEDGE_PATH, ADMIN_USERS_PATH} from '@/constants/admin'
</script>

<template>
  <div class="admin-home">
    <section class="welcome kk-glass kk-glass--panel">
      <h2 class="welcome-title">欢迎回来</h2>
      <p class="welcome-desc">
        这是 Khan Kiddo 管理后台。你可以在这里查看注册用户及其对话分析记录。
      </p>
    </section>

    <section class="quick-links">
      <h3 class="section-title">快捷入口</h3>
      <div class="link-grid">
        <router-link :to="ADMIN_USERS_PATH" class="link-card kk-glass kk-glass--panel">
          <span class="link-icon">
            <el-icon><User/></el-icon>
          </span>
          <div class="link-body">
            <p class="link-title">用户管理</p>
            <p class="link-desc">查看所有注册用户，浏览各用户的对话分析历史。</p>
          </div>
          <el-icon class="link-arrow"><ArrowRight/></el-icon>
        </router-link>
        <router-link :to="ADMIN_ANALYSES_PATH" class="link-card kk-glass kk-glass--panel">
          <span class="link-icon">
            <el-icon><ChatDotRound/></el-icon>
          </span>
          <div class="link-body">
            <p class="link-title">对话管理</p>
            <p class="link-desc">全站对话分析一览，按用户、内容或分析 ID 快速检索。</p>
          </div>
          <el-icon class="link-arrow"><ArrowRight/></el-icon>
        </router-link>
        <router-link :to="ADMIN_KNOWLEDGE_PATH" class="link-card kk-glass kk-glass--panel">
          <span class="link-icon">
            <el-icon><Collection/></el-icon>
          </span>
          <div class="link-body">
            <p class="link-title">知识点字典</p>
            <p class="link-desc">只读浏览错误叶子 pointId、家族结构与通道参数。</p>
          </div>
          <el-icon class="link-arrow"><ArrowRight/></el-icon>
        </router-link>
      </div>
    </section>
  </div>
</template>

<style scoped>
.admin-home {
  font-family: var(--kk-font-body);
  color: var(--kk-color-text);
  max-width: 960px;
}

.welcome {
  padding: 1.5rem 1.6rem;
  margin-bottom: 1.5rem;
}

.welcome-title {
  margin: 0 0 0.5rem;
  font-family: var(--kk-font-display);
  font-size: 1.35rem;
  font-weight: 700;
  color: var(--kk-color-primary);
}

.welcome-desc {
  margin: 0;
  line-height: 1.65;
  color: var(--kk-color-text-muted);
}

.section-title {
  margin: 0 0 0.85rem;
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--kk-color-text-secondary);
}

.link-grid {
  display: grid;
  gap: 0.75rem;
}

.link-card {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 1.1rem 1.25rem;
  text-decoration: none;
  color: inherit;
  transition: box-shadow var(--kk-duration-normal) ease;
}

.link-card:hover {
  box-shadow: var(--kk-shadow-card);
}

.link-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: var(--kk-glass-inner-bg);
  border: 1px solid var(--kk-glass-inner-border);
  color: var(--kk-color-primary);
  font-size: 1.25rem;
  flex-shrink: 0;
}

.link-body {
  flex: 1;
  min-width: 0;
}

.link-title {
  margin: 0 0 0.25rem;
  font-weight: 600;
  color: var(--kk-color-text);
}

.link-desc {
  margin: 0;
  font-size: 0.85rem;
  line-height: 1.5;
  color: var(--kk-color-text-muted);
}

.link-arrow {
  color: var(--kk-color-text-subtle);
  flex-shrink: 0;
}
</style>
