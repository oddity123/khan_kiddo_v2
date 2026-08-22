<script setup lang="ts">
import {ChatDotRound, Clock, Search, User, View} from '@element-plus/icons-vue'
import {ElMessage} from 'element-plus'
import {onMounted, ref} from 'vue'
import {useRouter} from 'vue-router'

import {listAdminUsers} from '@/api/admin'
import {adminUserAnalysesPath} from '@/constants/admin'
import type {AdminUserRow} from '@/types/admin'
import {getErrorMessage} from '@/utils/error'

const router = useRouter()

const loading = ref(true)
const keyword = ref('')
const minAnalysisCount = ref<number | undefined>()
const maxAnalysisCount = ref<number | undefined>()
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const records = ref<AdminUserRow[]>([])

function formatTime(value?: string) {
  if (!value) {
    return '—'
  }
  return value.replace('T', ' ').slice(0, 19)
}

function roleLabel(role: string) {
  return role === 'ADMIN' ? '管理员' : '普通用户'
}

function buildSearchParams() {
  return {
    page: page.value,
    size: pageSize.value,
    keyword: keyword.value.trim() || undefined,
    minAnalysisCount: minAnalysisCount.value,
    maxAnalysisCount: maxAnalysisCount.value,
  }
}

async function loadList() {
  loading.value = true
  try {
    const {data} = await listAdminUsers(buildSearchParams())
    records.value = data.records ?? []
    total.value = data.total ?? 0
  } catch (error) {
    records.value = []
    total.value = 0
    ElMessage.error(getErrorMessage(error, '加载用户列表失败'))
  } finally {
    loading.value = false
  }
}

function onSearch() {
  page.value = 1
  loadList()
}

function onPageChange(p: number) {
  page.value = p
  loadList()
}

function goUserAnalyses(user: AdminUserRow) {
  router.push({
    path: adminUserAnalysesPath(user.id),
    query: {username: user.username},
  })
}

onMounted(loadList)
</script>

<template>
  <div class="list-page">
    <header class="page-head">
      <p class="page-desc">查看所有注册用户，支持按用户名、邮箱及对话次数筛选。</p>
    </header>

    <section class="list-panel kk-glass kk-glass--panel">
      <div class="toolbar">
        <el-input
            v-model="keyword"
            placeholder="搜索用户名或邮箱"
            clearable
            class="search-input"
            @keyup.enter="onSearch"
        >
          <template #prefix>
            <el-icon><Search/></el-icon>
          </template>
        </el-input>
        <el-input-number
            v-model="minAnalysisCount"
            :min="0"
            :step="1"
            controls-position="right"
            placeholder="最少对话"
            class="count-input"
        />
        <el-input-number
            v-model="maxAnalysisCount"
            :min="0"
            :step="1"
            controls-position="right"
            placeholder="最多对话"
            class="count-input"
        />
        <el-button type="primary" :icon="Search" @click="onSearch">搜索</el-button>
      </div>

      <div v-loading="loading">
        <el-empty v-if="!loading && !records.length" description="暂无用户"/>

        <div v-else class="record-list">
          <article
              v-for="row in records"
              :key="row.id"
              class="record-card"
          >
            <div class="record-main" @click="goUserAnalyses(row)">
              <p class="record-title">
                <el-icon><User/></el-icon>
                {{ row.username }}
                <span v-if="row.role === 'ADMIN'" class="role-tag role-tag--admin">管理员</span>
              </p>
              <div class="record-meta">
                <span>邮箱 {{ row.email || '—' }}</span>
                <span><el-icon><Clock/></el-icon>注册 {{ formatTime(row.createdAt) }}</span>
                <span><el-icon><ChatDotRound/></el-icon>对话 {{ row.analysisCount ?? 0 }} 次</span>
                <span>{{ roleLabel(row.role) }}</span>
                <span>{{ row.enabled === false ? '已禁用' : '正常' }}</span>
              </div>
            </div>
            <div class="record-actions">
              <el-button text type="primary" :icon="View" @click="goUserAnalyses(row)">
                查看对话
              </el-button>
            </div>
          </article>
        </div>

        <div v-if="total > pageSize" class="pager">
          <el-pagination
              background
              layout="prev, pager, next, total"
              :total="total"
              :page-size="pageSize"
              :current-page="page"
              @current-change="onPageChange"
          />
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.list-page {
  font-family: var(--kk-font-body);
  color: var(--kk-color-text);
}

.page-head {
  margin-bottom: 1rem;
}

.page-desc {
  margin: 0;
  color: var(--kk-color-text-muted);
  line-height: 1.6;
}

.list-panel {
  padding: 1.25rem 1.35rem;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 0.65rem;
  margin-bottom: 1rem;
  align-items: center;
}

.search-input {
  flex: 1;
  min-width: 12rem;
  max-width: 24rem;
}

.count-input {
  width: 8.5rem;
}

.record-list {
  display: flex;
  flex-direction: column;
  gap: 0.65rem;
}

.record-card {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 1rem 1.1rem;
  border-radius: var(--kk-radius-md);
  background: var(--kk-glass-inner-bg);
  border: 1px solid var(--kk-glass-inner-border);
  transition: box-shadow var(--kk-duration-normal) ease;
}

.record-card:hover {
  box-shadow: var(--kk-shadow-card);
}

.record-main {
  flex: 1;
  min-width: 12rem;
  cursor: pointer;
}

.record-title {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.35rem;
  margin: 0 0 0.45rem;
  font-size: 1rem;
  font-weight: 600;
  color: var(--kk-color-text);
}

.record-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem 1rem;
  font-size: 0.8rem;
  color: var(--kk-color-text-subtle);
}

.record-meta span {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
}

.role-tag {
  padding: 0.1rem 0.45rem;
  border-radius: 999px;
  font-size: 0.72rem;
  font-weight: 600;
}

.role-tag--admin {
  background: #eef2ff;
  color: var(--kk-color-primary);
}

.record-actions {
  display: flex;
  gap: 0.25rem;
}

.pager {
  display: flex;
  justify-content: center;
  margin-top: 1.25rem;
}
</style>
