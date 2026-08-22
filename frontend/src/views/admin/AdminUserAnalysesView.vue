<script setup lang="ts">
import {Clock, Cpu, Document, Search, View} from '@element-plus/icons-vue'
import {ElMessage} from 'element-plus'
import {computed, onMounted, ref, watch} from 'vue'
import {useRoute, useRouter} from 'vue-router'

import {listAdminUserAnalyses} from '@/api/admin'
import {adminAnalysisDetailPath, ADMIN_ANALYSIS_FROM_USER} from '@/constants/admin'
import AnalysisHistoryScoreStrip from '@/components/conversation/AnalysisHistoryScoreStrip.vue'
import type {AnalysisSummaryRow} from '@/types/conversation'
import {getErrorMessage} from '@/utils/error'
import {
  adminListReturnTo,
  parseAdminListKeyword,
  parseAdminListPage,
  syncAdminListRouteQuery,
} from '@/utils/adminListRoute'

const route = useRoute()
const router = useRouter()

const userId = computed(() => Number(route.params.userId))
const username = computed(() => String(route.query.username ?? ''))

const loading = ref(true)
const keyword = ref(parseAdminListKeyword(route.query))
const page = ref(parseAdminListPage(route.query))
const pageSize = ref(10)
const total = ref(0)
const records = ref<AnalysisSummaryRow[]>([])

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
  if (!userId.value) {
    return
  }
  loading.value = true
  try {
    const {data} = await listAdminUserAnalyses(userId.value, {
      page: page.value,
      size: pageSize.value,
      keyword: keyword.value.trim() || undefined,
    })
    records.value = data.records ?? []
    total.value = data.total ?? 0
  } catch (error) {
    records.value = []
    total.value = 0
    ElMessage.error(getErrorMessage(error, '加载对话记录失败'))
  } finally {
    loading.value = false
  }
}

function onSearch() {
  page.value = 1
  syncAdminListRouteQuery(router, route, page.value, keyword.value)
  loadList()
}

function onPageChange(p: number) {
  page.value = p
  syncAdminListRouteQuery(router, route, page.value, keyword.value)
  loadList()
}

function goDetail(id: string) {
  router.push({
    path: adminAnalysisDetailPath(userId.value, id),
    query: {
      username: route.query.username,
      from: ADMIN_ANALYSIS_FROM_USER,
      returnTo: adminListReturnTo(router, route, page.value, keyword.value),
    },
  })
}

onMounted(() => {
  page.value = parseAdminListPage(route.query)
  keyword.value = parseAdminListKeyword(route.query)
  syncAdminListRouteQuery(router, route, page.value, keyword.value)
  loadList()
})

watch(() => route.params.userId, () => {
  page.value = 1
  keyword.value = ''
  syncAdminListRouteQuery(router, route, page.value, keyword.value)
  loadList()
})

watch(
  () => [route.query.page, route.query.keyword] as const,
  () => {
    const nextPage = parseAdminListPage(route.query)
    const nextKeyword = parseAdminListKeyword(route.query)
    if (nextPage === page.value && nextKeyword === keyword.value) {
      return
    }
    page.value = nextPage
    keyword.value = nextKeyword
    loadList()
  },
)
</script>

<template>
  <div class="list-page">
    <header class="page-head">
      <div>
        <h1 class="page-title">
          {{ username ? `${username} 的对话` : '用户对话' }}
        </h1>
        <p class="page-desc">用户 ID：{{ userId }} · 查看该用户保存的对话分析记录。</p>
      </div>
    </header>

    <section class="list-panel kk-glass kk-glass--panel">
      <div class="toolbar">
        <el-input
            v-model="keyword"
            placeholder="搜索对话预览内容"
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
        <el-empty v-if="!loading && !records.length" description="该用户暂无分析记录"/>

        <div v-else class="record-list">
          <article
              v-for="row in records"
              :key="row.analysisId"
              class="record-card"
          >
            <div class="record-main" @click="goDetail(row.analysisId)">
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
              <el-button text type="primary" :icon="View" @click="goDetail(row.analysisId)">
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
  margin-bottom: 1.25rem;
}

.page-title {
  margin: 0 0 0.35rem;
  font-family: var(--kk-font-display);
  font-size: clamp(1.5rem, 3vw, 2rem);
  font-weight: 800;
  color: var(--kk-color-primary);
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
