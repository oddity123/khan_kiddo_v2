<script setup lang="ts">
import {ChatLineRound, Promotion, View} from '@element-plus/icons-vue'
import type {FormInstance, FormRules} from 'element-plus'
import {ElMessage} from 'element-plus'
import DOMPurify from 'dompurify'
import {marked} from 'marked'
import {computed, onMounted, reactive, ref, watch} from 'vue'
import {storeToRefs} from 'pinia'

import {submitFeedback} from '@/api/feedback'
import {useAuthStore} from '@/stores/auth'
import {getErrorMessage} from '@/utils/error'

const auth = useAuthStore()
const {isAuthenticated, user} = storeToRefs(auth)

const formRef = ref<FormInstance>()
const submitting = ref(false)
const submitted = ref(false)

const form = reactive({
  title: '',
  email: '',
  content: '',
})

const rules: FormRules = {
  title: [
    {required: true, message: '请输入标题', trigger: 'blur'},
    {max: 200, message: '标题不能超过 200 个字符', trigger: 'blur'},
  ],
  email: [{type: 'email', message: '邮箱格式不正确', trigger: 'blur'}],
  content: [
    {required: true, message: '请输入具体内容', trigger: 'blur'},
    {max: 10000, message: '内容不能超过 10000 个字符', trigger: 'blur'},
  ],
}

const contentLength = computed(() => form.content.trim().length)

const previewHtml = computed(() => {
  const raw = form.content.trim()
  if (!raw) {
    return ''
  }
  try {
    const html = marked.parse(raw, {breaks: true, gfm: true}) as string
    return DOMPurify.sanitize(html)
  } catch {
    return DOMPurify.sanitize(`<pre>${escapeHtml(raw)}</pre>`)
  }
})

function escapeHtml(text: string) {
  return text
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
}

onMounted(async () => {
  if (!auth.initialized) {
    await auth.initialize()
  }
  if (isAuthenticated.value && user.value?.email) {
    form.email = user.value.email
  }
})

watch(
    () => user.value?.email,
    (email) => {
      if (email && !form.email) {
        form.email = email
      }
    },
)

function resetForm() {
  form.title = ''
  form.content = ''
  if (!isAuthenticated.value || !user.value?.email) {
    form.email = ''
  }
  submitted.value = false
  formRef.value?.clearValidate()
}

async function onSubmit() {
  if (!formRef.value) {
    return
  }
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }

  submitting.value = true
  try {
    const email = form.email.trim()
    const {data} = await submitFeedback({
      title: form.title.trim(),
      content: form.content.trim(),
      email: email || undefined,
    })
    submitted.value = true
    ElMessage.success(data.message)
    form.title = ''
    form.content = ''
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '提交失败，请稍后重试'))
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="feedback-page">
    <header class="page-head">
      <h1 class="page-title">给我留言</h1>
      <p class="page-desc">
        这是一个还在快速打磨中的 MVP 版本，非常需要你的想法和建议。
        欢迎反馈使用体验、功能需求、Bug 或任何让你困惑的地方。
      </p>
    </header>

    <el-alert
        v-if="submitted"
        class="success-alert"
        type="success"
        show-icon
        :closable="true"
        title="感谢你的反馈！"
        description="我们会尽快查看并持续优化产品。欢迎继续补充更多想法。"
        @close="submitted = false"
    />

    <section class="feedback-shell kk-glass kk-glass--panel">
      <div class="panel-head">
        <h2 class="panel-title">
          <el-icon><ChatLineRound/></el-icon>
          提交你的想法与建议
        </h2>
        <p class="panel-hint">支持 Markdown 编写，右侧可实时预览</p>
      </div>

      <el-form
          ref="formRef"
          class="feedback-form"
          :model="form"
          :rules="rules"
          label-position="top"
          size="large"
          @submit.prevent="onSubmit"
      >
        <div class="meta-row">
          <el-form-item class="meta-field" label="标题" prop="title">
            <el-input
                v-model="form.title"
                maxlength="200"
                show-word-limit
                placeholder="例如：对对话分析结果展示的一点建议"
            />
          </el-form-item>

          <el-form-item class="meta-field" label="联系方式邮箱（选填）" prop="email">
            <el-input
                v-model="form.email"
                maxlength="100"
                placeholder="方便的话留下邮箱，便于后续确认需求"
            />
            <p class="field-tip">仅用于必要时与你联系，不会用于任何营销用途。</p>
          </el-form-item>
        </div>

        <div class="editor-grid">
          <el-form-item class="editor-field" label="具体内容" prop="content">
            <el-input
                v-model="form.content"
                type="textarea"
                :rows="16"
                resize="vertical"
                placeholder="支持 Markdown，例如：&#10;# 使用场景&#10;- 我在备考雅思口语&#10;&#10;**问题**：分析结果里的分类不够直观&#10;&#10;**建议**：希望增加按问题类型筛选"
            />
            <div class="editor-meta">
              <span>已输入 {{ contentLength }} 字</span>
              <span>最多 10000 字</span>
            </div>
          </el-form-item>

          <aside class="preview-panel">
            <div class="preview-head">
              <span class="preview-label">
                <el-icon><View/></el-icon>
                实时预览
              </span>
              <span class="preview-count">{{ contentLength }} 字</span>
            </div>
            <div
                class="preview-body markdown-body"
                v-if="previewHtml"
                v-html="previewHtml"
            />
            <p v-else class="preview-empty">
              在左侧输入内容，这里会以 Markdown 方式实时预览。
            </p>
          </aside>
        </div>

        <div class="form-footer">
          <p class="footer-note">
            目前为个人项目自用工具，功能会根据反馈不断优化迭代。
          </p>
          <div class="footer-actions">
            <el-button size="large" :disabled="submitting" @click="resetForm">
              清空
            </el-button>
            <el-button
                type="primary"
                size="large"
                :loading="submitting"
                :icon="Promotion"
                native-type="submit"
            >
              提交反馈
            </el-button>
          </div>
        </div>
      </el-form>
    </section>
  </div>
</template>

<style scoped>
.feedback-page {
  max-width: 960px;
  margin: 0 auto;
  padding-bottom: 2.5rem;
}

.page-head {
  margin-bottom: 1.5rem;
  text-align: center;
}

.page-title {
  margin: 0 0 0.6rem;
  font-family: var(--kk-font-display);
  font-size: clamp(1.75rem, 4vw, 2.25rem);
  font-weight: 600;
  color: var(--kk-color-primary);
  letter-spacing: -0.02em;
}

.page-desc {
  margin: 0 auto;
  max-width: 38rem;
  font-family: var(--kk-font-body);
  font-size: 0.98rem;
  line-height: 1.65;
  color: var(--kk-color-text-muted);
}

.success-alert {
  margin-bottom: 1.25rem;
  border-radius: var(--kk-radius-md);
}

.feedback-shell {
  padding: 1.5rem 1.35rem 1.35rem;
  border-radius: var(--kk-radius-lg);
  box-shadow: var(--kk-shadow-card);
}

.panel-head {
  margin-bottom: 1.25rem;
  padding-bottom: 1rem;
  border-bottom: 1px solid var(--kk-glass-divider);
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  margin: 0 0 0.35rem;
  font-family: var(--kk-font-display);
  font-size: 1.2rem;
  font-weight: 600;
  color: var(--kk-color-primary);
}

.panel-hint {
  margin: 0;
  font-family: var(--kk-font-body);
  font-size: 0.9rem;
  color: var(--kk-color-text-muted);
}

.feedback-form :deep(.el-form-item__label) {
  font-family: var(--kk-font-body);
  font-weight: 600;
  color: var(--kk-color-text-secondary);
}

.meta-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1rem;
}

.field-tip {
  margin: 0.35rem 0 0;
  font-size: 0.82rem;
  line-height: 1.45;
  color: var(--kk-color-text-subtle);
}

.editor-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 1rem;
  align-items: start;
}

.editor-field {
  margin-bottom: 0;
}

.editor-meta {
  display: flex;
  justify-content: space-between;
  margin-top: 0.45rem;
  font-size: 0.82rem;
  color: var(--kk-color-text-subtle);
}

.preview-panel {
  min-height: 100%;
  padding: 0.85rem 1rem;
  border-radius: var(--kk-radius-md);
  background: var(--kk-glass-subtle-bg);
  border: 1px solid var(--kk-glass-divider);
}

.preview-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 0.75rem;
  padding-bottom: 0.55rem;
  border-bottom: 1px solid var(--kk-glass-divider);
}

.preview-label {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  font-family: var(--kk-font-body);
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--kk-color-text-secondary);
}

.preview-count {
  font-size: 0.8rem;
  color: var(--kk-color-text-subtle);
}

.preview-body {
  min-height: 18rem;
}

.preview-empty {
  margin: 0;
  min-height: 18rem;
  font-size: 0.9rem;
  line-height: 1.6;
  color: var(--kk-color-text-subtle);
}

.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3) {
  margin: 0.75rem 0 0.45rem;
  font-family: var(--kk-font-display);
  color: var(--kk-color-primary);
  line-height: 1.35;
}

.markdown-body :deep(p),
.markdown-body :deep(li) {
  margin: 0.35rem 0;
  font-family: var(--kk-font-body);
  font-size: 0.92rem;
  line-height: 1.65;
  color: var(--kk-color-text-secondary);
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  padding-left: 1.25rem;
}

.markdown-body :deep(code) {
  padding: 0.1rem 0.35rem;
  border-radius: 4px;
  font-size: 0.85em;
  background: rgba(11, 26, 125, 0.08);
}

.markdown-body :deep(pre) {
  margin: 0.5rem 0;
  padding: 0.75rem;
  overflow-x: auto;
  border-radius: var(--kk-radius-sm);
  background: rgba(11, 26, 125, 0.06);
}

.form-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  margin-top: 1.25rem;
  padding-top: 1rem;
  border-top: 1px solid var(--kk-glass-divider);
}

.footer-note {
  margin: 0;
  max-width: 26rem;
  font-size: 0.85rem;
  line-height: 1.5;
  color: var(--kk-color-text-subtle);
}

.footer-actions {
  display: flex;
  gap: 0.65rem;
  flex-shrink: 0;
}

@media (max-width: 900px) {
  .meta-row,
  .editor-grid {
    grid-template-columns: 1fr;
  }

  .form-footer {
    flex-direction: column;
    align-items: stretch;
  }

  .footer-actions {
    justify-content: flex-end;
  }
}
</style>
