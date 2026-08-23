<script setup lang="ts">
import {Collection, InfoFilled} from '@element-plus/icons-vue'
import {ElMessage} from 'element-plus'
import {computed, onMounted, ref} from 'vue'

import {getAdminPointDictionary} from '@/api/admin'
import {
  KNOWLEDGE_CHANNEL_FIELD_HELP,
  KNOWLEDGE_FAMILY_FIELD_HELP,
  KNOWLEDGE_POINT_FIELD_HELP,
  knowledgeFieldHelp,
} from '@/constants/knowledgeFieldHelp'
import type {
  AdminKnowledgeTreeNode,
  AdminPointDictionaryResponse,
  AdminPointFamilyView,
  AdminPointLeafView,
} from '@/types/adminKnowledge'
import {getErrorMessage} from '@/utils/error'

interface DetailField {
  key: string
  value: string | number
  help: string
}

const loading = ref(true)
const dictionary = ref<AdminPointDictionaryResponse | null>(null)
const selected = ref<AdminKnowledgeTreeNode | null>(null)
const showRawJson = ref(false)

const treeData = computed(() => {
  const data = dictionary.value
  if (!data) {
    return []
  }
  return data.channels.map((channel): AdminKnowledgeTreeNode => ({
    id: `channel:${channel.channel}`,
    label: `${channel.labelZh}（${channel.pointCount} 叶子）`,
    type: 'channel',
    channel: channel.channel,
    children: data.families
        .filter((family) => family.channel === channel.channel)
        .map((family) => buildFamilyNode(family)),
  }))
})

const detailTitle = computed(() => {
  if (!selected.value) {
    return '选择左侧节点查看详情'
  }
  if (selected.value.type === 'channel') {
    return selected.value.label
  }
  if (selected.value.type === 'family') {
    return `${selected.value.family?.titleZh} · ${selected.value.family?.familyId}`
  }
  return selected.value.point?.pointId ?? ''
})

const detailFields = computed((): DetailField[] => {
  const node = selected.value
  if (!node) {
    return []
  }
  if (node.type === 'family' && node.family) {
    return familyFields(node.family)
  }
  if (node.type === 'point' && node.point) {
    return pointFields(node.point)
  }
  if (node.type === 'channel' && node.channel && dictionary.value) {
    const summary = dictionary.value.channels.find((item) => item.channel === node.channel)
    return [
      field('通道', node.channel, KNOWLEDGE_CHANNEL_FIELD_HELP),
      field('中文名', summary?.labelZh ?? '—', KNOWLEDGE_CHANNEL_FIELD_HELP),
      field('家族数', summary?.familyCount ?? 0, KNOWLEDGE_CHANNEL_FIELD_HELP),
      field('叶子数', summary?.pointCount ?? 0, KNOWLEDGE_CHANNEL_FIELD_HELP),
    ]
  }
  return []
})

const rawJson = computed(() => {
  const node = selected.value
  if (!node) {
    return ''
  }
  if (node.type === 'family' && node.family) {
    return JSON.stringify(node.family, null, 2)
  }
  if (node.type === 'point' && node.point) {
    return JSON.stringify(node.point, null, 2)
  }
  if (node.type === 'channel' && node.channel && dictionary.value) {
    const payload = {
      channel: node.channel,
      families: dictionary.value.families.filter((family) => family.channel === node.channel),
    }
    return JSON.stringify(payload, null, 2)
  }
  return ''
})

function buildFamilyNode(family: AdminPointFamilyView): AdminKnowledgeTreeNode {
  return {
    id: `family:${family.familyId}`,
    label: `${family.titleZh}（${family.pointCount}）`,
    type: 'family',
    family,
    children: family.points.map((point) => ({
      id: `point:${point.pointId}`,
      label: pointTitle(point),
      type: 'point',
      point,
    })),
  }
}

function pointTitle(point: AdminPointLeafView) {
  const tags: string[] = []
  if (point.globalFallback) {
    tags.push('全局兜底')
  } else if (point.familyOtherLeaf) {
    tags.push('家族兜底')
  } else if (point.catchAllLeaf) {
    tags.push('兜底')
  }
  if (point.cardPolicy === 'rare') {
    tags.push('仅分类')
  }
  return tags.length ? `${point.pointId} · ${tags.join(' / ')}` : point.pointId
}

function field(key: string, value: string | number, helpMap: Record<string, string>): DetailField {
  return {
    key,
    value,
    help: knowledgeFieldHelp(helpMap, key),
  }
}

function familyFields(family: AdminPointFamilyView): DetailField[] {
  return [
    field('familyId', family.familyId, KNOWLEDGE_FAMILY_FIELD_HELP),
    field('titleZh', family.titleZh, KNOWLEDGE_FAMILY_FIELD_HELP),
    field('channel', family.channel, KNOWLEDGE_FAMILY_FIELD_HELP),
    field('habitUnit', family.habitUnit, KNOWLEDGE_FAMILY_FIELD_HELP),
    field('fixability', family.fixability ?? '—', KNOWLEDGE_FAMILY_FIELD_HELP),
    field('impactWeight', family.impactWeight, KNOWLEDGE_FAMILY_FIELD_HELP),
    field('otherPointId', family.otherPointId, KNOWLEDGE_FAMILY_FIELD_HELP),
    field('pointCount', family.pointCount, KNOWLEDGE_FAMILY_FIELD_HELP),
  ]
}

function pointFields(point: AdminPointLeafView): DetailField[] {
  return [
    field('pointId', point.pointId, KNOWLEDGE_POINT_FIELD_HELP),
    field('familyId', point.familyId, KNOWLEDGE_POINT_FIELD_HELP),
    field('channel', point.channel, KNOWLEDGE_POINT_FIELD_HELP),
    field('cardKind', point.cardKind, KNOWLEDGE_POINT_FIELD_HELP),
    field('cardPolicy', point.cardPolicy, KNOWLEDGE_POINT_FIELD_HELP),
    field('habitUnit', point.habitUnit, KNOWLEDGE_POINT_FIELD_HELP),
    field('impactWeight', point.impactWeight, KNOWLEDGE_POINT_FIELD_HELP),
    field('fixability', point.fixability ?? '—', KNOWLEDGE_POINT_FIELD_HELP),
    field('errorLevel', point.errorLevel ?? '—', KNOWLEDGE_POINT_FIELD_HELP),
    field('scoreProfile', point.scoreProfile ?? '—', KNOWLEDGE_POINT_FIELD_HELP),
    field('titleZh', point.titleZh ?? '—', KNOWLEDGE_POINT_FIELD_HELP),
    field('catchAllLeaf', point.catchAllLeaf ? '是' : '否', KNOWLEDGE_POINT_FIELD_HELP),
    field('globalFallback', point.globalFallback ? '是' : '否', KNOWLEDGE_POINT_FIELD_HELP),
    field('familyOtherLeaf', point.familyOtherLeaf ? '是' : '否', KNOWLEDGE_POINT_FIELD_HELP),
  ]
}

function onNodeClick(node: AdminKnowledgeTreeNode) {
  selected.value = node
  showRawJson.value = node.type !== 'channel'
}

async function loadDictionary() {
  loading.value = true
  try {
    const {data} = await getAdminPointDictionary()
    dictionary.value = data
    if (treeData.value.length) {
      selected.value = treeData.value[0]
    }
  } catch (error) {
    dictionary.value = null
    ElMessage.error(getErrorMessage(error, '加载知识点字典失败'))
  } finally {
    loading.value = false
  }
}

onMounted(loadDictionary)
</script>

<template>
  <div v-loading="loading" class="dict-page">
    <header v-if="dictionary" class="stats kk-glass kk-glass--panel">
      <div class="stats-main">
        <span class="stats-icon"><el-icon><Collection/></el-icon></span>
        <div>
          <p class="stats-title">运行时知识点字典</p>
          <p class="stats-meta">
            版本 {{ dictionary.version }} ·
            {{ dictionary.stats.familyCount }} 个家族 ·
            {{ dictionary.stats.pointCount }} 个错误叶子
          </p>
        </div>
      </div>
      <div class="stats-channels">
        <span
            v-for="channel in dictionary.channels"
            :key="channel.channel"
            class="channel-chip"
        >
          {{ channel.labelZh }} {{ channel.pointCount }}
        </span>
      </div>
    </header>

    <div class="dict-body">
      <aside class="dict-tree kk-glass kk-glass--panel">
        <p class="panel-title">通道 · 家族 · 叶子</p>
        <el-tree
            :data="treeData"
            node-key="id"
            default-expand-all
            highlight-current
            :expand-on-click-node="false"
            @node-click="onNodeClick"
        />
      </aside>

      <section class="dict-detail kk-glass kk-glass--panel">
        <h2 class="detail-title">{{ detailTitle }}</h2>

        <dl v-if="detailFields.length" class="field-grid">
          <template v-for="item in detailFields" :key="item.key">
            <dt>
              <span>{{ item.key }}</span>
              <el-tooltip :content="item.help" placement="top" :show-after="200">
                <el-icon class="field-help-icon" aria-label="字段说明"><InfoFilled/></el-icon>
              </el-tooltip>
            </dt>
            <dd>{{ item.value }}</dd>
          </template>
        </dl>

        <div v-if="rawJson" class="raw-json">
          <button type="button" class="raw-toggle" @click="showRawJson = !showRawJson">
            {{ showRawJson ? '收起' : '查看' }}原始 JSON
          </button>
          <pre v-if="showRawJson">{{ rawJson }}</pre>
        </div>

        <el-empty v-if="!selected" description="点击左侧树节点查看详情"/>
      </section>
    </div>

    <section v-if="dictionary?.discriminators.length" class="discriminators kk-glass kk-glass--panel">
      <h2 class="section-title">Stage2 判据（discriminators）</h2>
      <div class="disc-list">
        <article v-for="item in dictionary.discriminators" :key="item.id" class="disc-item">
          <p class="disc-id">{{ item.id }}</p>
          <p class="disc-rule">{{ item.rule }}</p>
        </article>
      </div>
    </section>
  </div>
</template>

<style scoped>
.dict-page {
  font-family: var(--kk-font-body);
  color: var(--kk-color-text);
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.stats {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 1rem 1.2rem;
}

.stats-main {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.stats-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border-radius: 12px;
  background: var(--kk-glass-inner-bg);
  border: 1px solid var(--kk-glass-inner-border);
  color: var(--kk-color-primary);
  font-size: 1.2rem;
}

.stats-title {
  margin: 0 0 0.2rem;
  font-family: var(--kk-font-display);
  font-weight: 700;
  color: var(--kk-color-primary);
}

.stats-meta {
  margin: 0;
  font-size: 0.85rem;
  color: var(--kk-color-text-muted);
}

.stats-channels {
  display: flex;
  flex-wrap: wrap;
  gap: 0.45rem;
}

.channel-chip {
  padding: 0.25rem 0.65rem;
  border-radius: 999px;
  font-size: 0.78rem;
  background: var(--kk-glass-inner-bg);
  border: 1px solid var(--kk-glass-inner-border);
  color: var(--kk-color-text-secondary);
}

.dict-body {
  display: grid;
  grid-template-columns: minmax(240px, 320px) minmax(0, 1fr);
  gap: 1rem;
  align-items: start;
}

.dict-tree,
.dict-detail,
.discriminators {
  padding: 1rem 1.1rem;
}

.panel-title {
  margin: 0 0 0.75rem;
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--kk-color-text-secondary);
}

.detail-title {
  margin: 0 0 1rem;
  font-family: var(--kk-font-display);
  font-size: 1.15rem;
  font-weight: 700;
  color: var(--kk-color-primary);
}

.field-grid {
  display: grid;
  grid-template-columns: 9rem minmax(0, 1fr);
  gap: 0.45rem 0.75rem;
  margin: 0 0 1rem;
}

.field-grid dt {
  margin: 0;
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  font-size: 0.78rem;
  font-weight: 600;
  color: var(--kk-color-text-subtle);
}

.field-help-icon {
  font-size: 0.85rem;
  color: var(--kk-color-text-subtle);
  cursor: help;
  opacity: 0.55;
  transition: opacity var(--kk-duration-normal) ease, color var(--kk-duration-normal) ease;
}

.field-help-icon:hover {
  opacity: 1;
  color: var(--kk-color-primary);
}

.field-grid dd {
  margin: 0;
  font-size: 0.88rem;
  line-height: 1.5;
  color: var(--kk-color-text);
  word-break: break-word;
}

.raw-json {
  margin-top: 1rem;
}

.raw-toggle {
  border: none;
  background: none;
  padding: 0;
  color: var(--kk-color-primary);
  font-weight: 600;
  cursor: pointer;
  font-family: inherit;
}

.raw-json pre {
  margin: 0.65rem 0 0;
  padding: 0.85rem 1rem;
  border-radius: var(--kk-radius-md);
  background: var(--kk-glass-inner-bg);
  border: 1px solid var(--kk-glass-inner-border);
  overflow: auto;
  font-family: var(--kk-font-mono);
  font-size: 0.78rem;
  line-height: 1.55;
}

.section-title {
  margin: 0 0 0.85rem;
  font-family: var(--kk-font-display);
  font-size: 1rem;
  font-weight: 700;
  color: var(--kk-color-primary);
}

.disc-list {
  display: grid;
  gap: 0.65rem;
}

.disc-item {
  padding: 0.75rem 0.85rem;
  border-radius: var(--kk-radius-md);
  background: var(--kk-glass-inner-bg);
  border: 1px solid var(--kk-glass-inner-border);
}

.disc-id {
  margin: 0 0 0.25rem;
  font-family: var(--kk-font-mono);
  font-size: 0.82rem;
  font-weight: 600;
  color: var(--kk-color-primary);
}

.disc-rule {
  margin: 0;
  font-size: 0.85rem;
  line-height: 1.6;
  color: var(--kk-color-text-muted);
}

@media (max-width: 960px) {
  .dict-body {
    grid-template-columns: 1fr;
  }
}
</style>
