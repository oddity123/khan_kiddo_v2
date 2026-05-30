<script setup lang="ts">
import { Connection } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { onMounted } from 'vue'

import { fetchHealth } from '@/api/health'
import { useAppStore } from '@/stores/app'
import { getErrorMessage } from '@/utils/error'

const appStore = useAppStore()

async function checkBackend() {
  appStore.setBackendLoading(true)
  try {
    const { data } = await fetchHealth()
    appStore.setBackendStatus(data.status)
    ElMessage.success(`后端正常：${data.application} (${data.status})`)
  } catch (error) {
    appStore.setBackendStatus(null)
    ElMessage.error(getErrorMessage(error, '无法连接后端，请确认 backend 已在 8081 端口启动'))
  } finally {
    appStore.setBackendLoading(false)
  }
}

onMounted(() => {
  checkBackend()
})
</script>

<template>
  <el-card shadow="never">
    <template #header>
      <div class="card-header">
        <span>欢迎使用 Khan Kiddo v2</span>
        <el-tag type="success">Vue 3 + Element Plus</el-tag>
      </div>
    </template>

    <p class="desc">
      前后端分离骨架已就绪。开发时 Vite 会将 <code>/api</code> 代理到
      <code>http://localhost:8081</code>。
    </p>

    <el-descriptions :column="1" border class="meta">
      <el-descriptions-item label="前端">
        Vue 3 · Vite · TypeScript · Pinia · Vue Router · Element Plus
      </el-descriptions-item>
      <el-descriptions-item label="后端">
        Spring Boot 3 · Java 21 · 端口 8081
      </el-descriptions-item>
      <el-descriptions-item label="后端状态">
        <el-tag v-if="appStore.backendStatus" type="success">
          {{ appStore.backendStatus }}
        </el-tag>
        <el-tag v-else type="info">未检测</el-tag>
      </el-descriptions-item>
    </el-descriptions>

    <div class="actions">
      <el-button
        type="primary"
        :icon="Connection"
        :loading="appStore.backendLoading"
        @click="checkBackend"
      >
        检测后端连接
      </el-button>
    </div>
  </el-card>
</template>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.desc {
  margin: 0 0 16px;
  color: #606266;
  line-height: 1.6;
}

.meta {
  margin-bottom: 20px;
}

.actions {
  display: flex;
  gap: 12px;
}

code {
  padding: 2px 6px;
  border-radius: 4px;
  background: #f4f4f5;
  font-size: 0.9em;
}
</style>
