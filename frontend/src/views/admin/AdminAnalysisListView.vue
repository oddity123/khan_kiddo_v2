<script setup lang="ts">
import {Clock, Cpu, Document, Search, User, View} from '@element-plus/icons-vue'
import {ElMessage} from 'element-plus'
import {onMounted, ref, watch} from 'vue'
import {useRoute, useRouter} from 'vue-router'

import {listAdminAnalyses} from '@/api/admin'
import {ADMIN_ANALYSIS_FROM_ANALYSES, adminAnalysisDetailPath, adminUserAnalysesPath} from '@/constants/admin'
import AnalysisHistoryScoreStrip from '@/components/conversation/AnalysisHistoryScoreStrip.vue'
import type {AdminAnalysisRow} from '@/types/admin'
import {getErrorMessage} from '@/utils/error'
import {
  adminAnalysesListReturnTo,
  parseAdminListKeyword,
  parseAdminListPage,
  parseAdminListUsername,
  syncAdminAnalysesListRouteQuery,
} from '@/utils/adminListRoute'

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const keyword = ref(parseAdminListKeyword(route.query))
const username = ref(parseAdminListUsername(route.query))
const page = ref(parseAdminListPage(route.query))
const pageSize = ref(10)
const total = ref(0)
const records = ref<AdminAnalysisRow[]>([])

function formatTime(value?: string) {
  if (!value) {
    return '—'
  }
  return value.replace('T', ' ').slice(0, 19)
}

function statusLabel(status: string) {
  if (status === 'success') {
    return '已完成'
  }
  if (status === 'failed') {
    return '失败'
  }
  return status
}

function statusClass(status: string) {
  if (status === 'failed') {
    return 'status-tag--failed'
  }
  return 'status-tag--success'
}

function formatDuration(ms?: number) {
  if (ms == null) {
    return '—'
  }
  if (ms < 1000) {
    return `${ms} ms`
  }
  return `${(ms / 1000).toFixed(1)} s`
}

function formatCharCount(count?: number) {
  if (count == null) {
    return '—'
  }
  return `${count} 字`
}

async function loadList() {
  loading.value = true
  try {
    const {data} = await listAdminAnalyses({
      page: page.value,
      size: pageSize.value,
      keyword: keyword.value.trim() || undefined,
      username: username.value.trim() || undefined,
    })
    records.value = data.records ?? []
    total.value = data.total ?? 0
  } catch (error) {
    records.value = []
    total.value = 0
    ElMessage.error(getErrorMessage(error, '加载对话列表失败'))
  } finally {
    loading.value = false
  }
}

function onSearch() {
  page.value = 1
  syncAdminAnalysesListRouteQuery(router, route, page.value, keyword.value, username.value)
  loadList()
}

function onPageChange(p: number) {
  page.value = p
  syncAdminAnalysesListRouteQuery(router, route, page.value, keyword.value, username.value)
  loadList()
}

function goDetail(row: AdminAnalysisRow) {
  router.push({
    path: adminAnalysisDetailPath(row.userId, row.analysisId),
    query: {
      username: row.username,
      from: ADMIN_ANALYSIS_FROM_ANALYSES,
      returnTo: adminAnalysesListReturnTo(router, route, page.value, keyword.value, username.value),
    },
  })
}

function goUserAnalyses(row: AdminAnalysisRow) {
  router.push({
    path: adminUserAnalysesPath(row.userId),
    query: {username: row.username},
  })
}

onMounted(() => {
  page.value = parseAdminListPage(route.query)
  keyword.value = parseAdminListKeyword(route.query)
  username.value = parseAdminListUsername(route.query)
  syncAdminAnalysesListRouteQuery(router, route, page.value, keyword.value, username.value)
  loadList()
})

watch(
  () => [route.query.page, route.query.keyword, route.query.username] as const,
  () => {
    const nextPage = parseAdminListPage(route.query)
    const nextKeyword = parseAdminListKeyword(route.query)
    const nextUsername = parseAdminListUsername(route.query)
    if (nextPage === page.value && nextKeyword === keyword.value && nextUsername === username.value) {
      return
    }
    page.value = nextPage
    keyword.value = nextKeyword
    username.value = nextUsername
    loadList()
  },
)
</script>

<template>
  <div class="list-page">
    <header class="page-head">
      <p class="page-desc">查看全站对话分析记录，支持按用户名、对话内容或分析 ID 搜索。</p>
    </header>

    <section class="list-panel kk-glass kk-glass--panel">
      <div class="toolbar">
        <el-input
            v-model="username"
            placeholder="搜索用户名"
            clearable
            class="username-input"
            @keyup.enter="onSearch"
        >
          <template #prefix>
            <el-icon><User/></el-icon>
          </template>
        </el-input>
        <el-input
            v-model="keyword"
            placeholder="搜索对话内容或分析 ID"
            clearable
            class="search-input"
            @keyup.enter="onSearch"
        >
          <template #prefix>
            <el-icon><Search/></el-icon>
          </template>
        </el-input>
        <el-button type="primary" :icon="Search" @click="onSearch">搜索</el-button>
      </div>

      <div v-loading="loading">
        <el-empty v-if="!loading && !records.length" description="暂无对话记录"/>

        <div v-else class="record-list">
          <article
              v-for="row in records"
              :key="row.analysisId"
              class="record-card"
          >
            <div class="record-main" @click="goDetail(row)">
              <p class="record-user">
                <el-icon><User/></el-icon>
                <button type="button" class="user-link" @click.stop="goUserAnalyses(row)">
                  {{ row.username }}
                </button>
                <span class="user-id">ID {{ row.userId }}</span>
              </p>
              <p class="record-preview">{{ row.preview || '（无预览）' }}</p>
              <div class="record-meta">
                <span><el-icon><Clock/></el-icon>{{ formatTime(row.createdAt) }}</span>
                <span><el-icon><Document/></el-icon>{{ formatCharCount(row.contentCharCount) }}</span>
                <span v-if="row.llmModelName">
                  <el-icon><Cpu/></el-icon>{{ row.llmModelName }}
                </span>
                <span>耗时 {{ formatDuration(row.processingTimeMs) }}</span>
                <span class="status-tag" :class="statusClass(row.status)">{{ statusLabel(row.status) }}</span>
              </div>
              <AnalysisHistoryScoreStrip
                  v-if="row.status === 'success'"
                  :performance-score="row.performanceScore"
                  :dimension-scores="row.dimensionScores"
              />
            </div>
            <div class="record-actions">
              <el-button text type="primary" :icon="View" @click="goDetail(row)">
                详情
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
}

.search-input {
  flex: 1;
  min-width: 12rem;
  max-width: 24rem;
}

.username-input {
  width: 11rem;
  min-width: 9rem;
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

.record-user {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.35rem;
  margin: 0 0 0.35rem;
  font-size: 0.85rem;
  color: var(--kk-color-text-secondary);
}

.user-link {
  padding: 0;
  border: none;
  background: none;
  font: inherit;
  font-weight: 600;
  color: var(--kk-color-primary);
  cursor: pointer;
}

.user-link:hover {
  text-decoration: underline;
}

.user-id {
  font-size: 0.75rem;
  color: var(--kk-color-text-subtle);
}

.record-preview {
  margin: 0 0 0.45rem;
  font-size: 0.92rem;
  line-height: 1.55;
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

.status-tag {
  padding: 0.1rem 0.45rem;
  border-radius: 999px;
  font-weight: 600;
}

.status-tag--success {
  background: #edf7f0;
  color: #2d6a4f;
}

.status-tag--failed {
  background: #ffecec;
  color: #a01818;
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
