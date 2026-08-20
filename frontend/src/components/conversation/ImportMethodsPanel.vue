<script setup lang="ts">
import {ChatDotRound, CopyDocument, DocumentCopy, Monitor} from '@element-plus/icons-vue'
import {ElMessage} from 'element-plus'
import {ref} from 'vue'
import chromeIcon from '@/assets/icons/chrome.ico'
import edgeIcon from '@/assets/icons/edge.ico'

const CHROME_WEBSTORE_URL =
    'https://chromewebstore.google.com/detail/khan-kiddo/clgpfpccbjceihdjcpacihfmgenhaggj'
const EDGE_ADDON_URL =
    'https://microsoftedge.microsoft.com/addons/detail/khan-kiddo/pmbinepmifhmejifegcdmnjcdojpmoao'

const emit = defineEmits<{
  focusInput: []
}>()

/** 让 ChatGPT 原封不动输出本次对话全文，便于粘贴回本系统 */
const CHATGPT_EXPORT_PROMPT = `请将我们此前的对话内容原封不动地完整输出。

要求：
1. 只导出你收到本条指令之前的对话，不要包含本条指令本身（有的模型会误把提示词也写进输出，请务必排除）
2. 按发言顺序逐条列出，标明说话人（User / Assistant）
3. 不要改写、总结或省略任何一句
4. 不要添加解释、前言或结尾，只输出对话原文`

const promptCopied = ref(false)
let copyResetTimer: ReturnType<typeof setTimeout> | null = null

async function copyPrompt() {
  try {
    await navigator.clipboard.writeText(CHATGPT_EXPORT_PROMPT)
    promptCopied.value = true
    ElMessage.success('提示词已复制，去 ChatGPT 粘贴发送即可')
    if (copyResetTimer) {
      clearTimeout(copyResetTimer)
    }
    copyResetTimer = setTimeout(() => {
      promptCopied.value = false
    }, 2000)
  } catch {
    ElMessage.error('复制失败，请稍后重试')
  }
}

function onPasteMethod() {
  emit('focusInput')
}
</script>

<template>
  <section class="import-panel kk-glass kk-glass--panel" aria-labelledby="import-methods-title">
    <header class="panel-head">
      <h2 id="import-methods-title" class="panel-title">导入对话</h2>
      <p class="panel-desc">任选一种，把内容放到左侧</p>
    </header>

    <ol class="method-list">
      <li class="method">
        <span class="method-icon" aria-hidden="true">
          <el-icon><DocumentCopy/></el-icon>
        </span>
        <div class="method-body">
          <div class="method-row">
            <h3 class="method-name">全选粘贴</h3>
            <span class="method-tag">最简单</span>
          </div>
          <p class="method-text">在 ChatGPT 全选对话，复制后贴到左侧。</p>
          <button type="button" class="method-action" @click="onPasteMethod">
            去粘贴
          </button>
        </div>
      </li>

      <li class="method">
        <span class="method-icon" aria-hidden="true">
          <el-icon><ChatDotRound/></el-icon>
        </span>
        <div class="method-body">
          <div class="method-row">
            <h3 class="method-name">提示词导出</h3>
            <span class="method-tag method-tag--accent">长对话</span>
          </div>
          <p class="method-text">复制提示词发给 ChatGPT，再把原文贴回左侧。</p>
          <button
              type="button"
              class="method-action method-action--primary"
              :class="{'method-action--done': promptCopied}"
              @click="copyPrompt"
          >
            <el-icon><CopyDocument/></el-icon>
            {{ promptCopied ? '已复制' : '复制提示词' }}
          </button>
        </div>
      </li>

      <li class="method">
        <span class="method-icon" aria-hidden="true">
          <el-icon><Monitor/></el-icon>
        </span>
        <div class="method-body">
          <div class="method-row">
            <h3 class="method-name">浏览器插件</h3>
            <span class="method-tag method-tag--accent">Chrome / Edge</span>
          </div>
          <p class="method-text">安装后，点击 ChatGPT 的分享按钮，粘贴到插件中即可一键导入。</p>
          <p class="method-text">或者直接打开 ChatGPT 分享页，点击右下角的按钮一键导入。</p>
          <div class="method-actions">
            <a
                class="method-action method-action--primary"
                :href="CHROME_WEBSTORE_URL"
                target="_blank"
                rel="noopener noreferrer"
            >
              <img class="store-icon" :src="chromeIcon" alt="" width="15" height="15"/>
               安装
            </a>
            <a
                class="method-action method-action--primary"
                :href="EDGE_ADDON_URL"
                target="_blank"
                rel="noopener noreferrer"
            >
              <img class="store-icon" :src="edgeIcon" alt="" width="15" height="15"/>
               安装
            </a>
          </div>
        </div>
      </li>
    </ol>
  </section>
</template>

<style scoped>
.import-panel {
  padding: 1.05rem 1.2rem 1.15rem;
}

.panel-head {
  margin-bottom: 0.15rem;
  padding-bottom: 0.7rem;
  border-bottom: 1px solid var(--kk-color-border);
}

.panel-title {
  margin: 0 0 0.15rem;
  font-family: var(--kk-font-display);
  font-size: 1.05rem;
  font-weight: 700;
  color: var(--kk-color-primary);
  letter-spacing: -0.02em;
}

.panel-desc {
  margin: 0;
  font-size: 0.78rem;
  line-height: 1.4;
  color: var(--kk-color-text-muted);
}

.method-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.method {
  display: grid;
  grid-template-columns: 1.35rem 1fr;
  gap: 0.55rem;
  padding: 0.75rem 0;
  border-bottom: 1px solid var(--kk-color-border);
  opacity: 0;
  transform: translateY(6px);
  animation: method-in 0.35s ease forwards;
}

.method:nth-child(1) {
  animation-delay: 0.04s;
}

.method:nth-child(2) {
  animation-delay: 0.1s;
}

.method:nth-child(3) {
  animation-delay: 0.16s;
}

.method:last-child {
  border-bottom: none;
  padding-bottom: 0.15rem;
}

@keyframes method-in {
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.method-icon {
  display: inline-flex;
  align-items: flex-start;
  justify-content: center;
  padding-top: 0.12rem;
  color: var(--kk-color-primary);
  font-size: 1rem;
  line-height: 1;
}

.method-body {
  min-width: 0;
}

.method-row {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 0.3rem 0.45rem;
  margin-bottom: 0.2rem;
}

.method-name {
  margin: 0;
  font-size: 0.88rem;
  font-weight: 700;
  color: var(--kk-color-primary);
  line-height: 1.3;
}

.method-tag {
  font-size: 0.65rem;
  font-weight: 700;
  letter-spacing: 0.03em;
  color: var(--kk-color-text-subtle);
}

.method-tag--accent {
  color: var(--kk-color-accent-text);
}

.method-text {
  margin: 0 0 0.45rem;
  font-size: 0.78rem;
  line-height: 1.45;
  color: var(--kk-color-text-muted);
}

.method-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem 0.55rem;
}

.store-icon {
  width: 0.95rem;
  height: 0.95rem;
  flex-shrink: 0;
  display: block;
  object-fit: contain;
}

.method-action {
  display: inline-flex;
  align-items: center;
  gap: 0.28rem;
  appearance: none;
  margin: 0;
  padding: 0;
  border: none;
  background: none;
  font-family: var(--kk-font-body);
  font-size: 0.78rem;
  font-weight: 700;
  color: var(--kk-color-link);
  text-decoration: none;
  cursor: pointer;
  transition: color 0.15s ease;
}

.method-action:hover {
  color: var(--kk-color-primary);
}

.method-action--primary {
  padding: 0.28rem 0.65rem;
  border-radius: var(--kk-radius-sm);
  border: 1px solid color-mix(in srgb, var(--kk-color-primary) 22%, transparent);
  color: var(--kk-color-primary);
  transition:
      background 0.15s ease,
      border-color 0.15s ease,
      color 0.15s ease;
}

.method-action--primary:hover {
  background: color-mix(in srgb, var(--kk-color-primary) 5%, transparent);
  border-color: color-mix(in srgb, var(--kk-color-primary) 36%, transparent);
  color: var(--kk-color-primary);
}

.method-action--done {
  border-color: color-mix(in srgb, var(--kk-color-success) 35%, transparent);
  color: var(--kk-color-success);
}

.method-action--done:hover {
  background: var(--kk-color-success-bg);
  border-color: color-mix(in srgb, var(--kk-color-success) 35%, transparent);
  color: var(--kk-color-success);
}

@media (max-width: 992px) {
  .import-panel {
    padding: 0.95rem 1.05rem 1.05rem;
  }

  .method {
    padding: 0.65rem 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .method {
    animation: none;
    opacity: 1;
    transform: none;
  }
}
</style>
