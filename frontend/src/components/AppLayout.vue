<script setup lang="ts">
import { House } from '@element-plus/icons-vue'
import { RouterView, useRoute } from 'vue-router'

const route = useRoute()

const menuItems = [{ path: '/', label: '首页', icon: House }]
</script>

<template>
  <el-container class="layout">
    <el-aside width="220px" class="layout-aside">
      <div class="brand">Khan Kiddo v2</div>
      <el-menu
        :default-active="route.path"
        router
        class="layout-menu"
      >
        <el-menu-item
          v-for="item in menuItems"
          :key="item.path"
          :index="item.path"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="layout-header">
        <span class="layout-header-title">{{ route.meta.title }}</span>
      </el-header>
      <el-main class="layout-main">
        <RouterView />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout {
  min-height: 100vh;
}

.layout-aside {
  background: #304156;
  color: #fff;
}

.brand {
  padding: 20px 16px;
  font-size: 18px;
  font-weight: 600;
  letter-spacing: 0.02em;
}

.layout-menu {
  border-right: none;
  background: transparent;
}

.layout-header {
  display: flex;
  align-items: center;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
}

.layout-header-title {
  font-size: 16px;
  font-weight: 500;
}

.layout-main {
  padding: 24px;
}
</style>
